#!/bin/bash
set -euo pipefail

# ============================================================
# 中间件资源管理平台 - 生产部署脚本
# 用法: ./deploy.sh <tar包路径> [模块: all|backend|frontend|db]
# 示例: ./deploy.sh /tmp/middleware-resource-manager-v1.0.8-20260602.tar.gz
#       ./deploy.sh /tmp/middleware-resource-manager-v1.0.8-20260602.tar.gz backend
# ============================================================

DEPLOY_DIR="/app/infra_portal"
NGINX_DIR="/app/nginx/nginx_home"
MYSQL="/app/mysql_client/bin/mysql"
DB_NAME="middleware_resource_manager"
BACKUP_DIR="/app/infra_portal_backups"
LOG_FILE="/tmp/deploy-$(date +%Y%m%d%H%M%S).log"

# 颜色
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log()  { echo -e "${GREEN}[$(date '+%H:%M:%S')]${NC} $*" | tee -a "$LOG_FILE"; }
warn() { echo -e "${YELLOW}[$(date '+%H:%M:%S')] WARN:${NC} $*" | tee -a "$LOG_FILE"; }
err()  { echo -e "${RED}[$(date '+%H:%M:%S')] ERROR:${NC} $*" | tee -a "$LOG_FILE"; }

# --- 参数校验 ---
if [[ $# -lt 1 ]]; then
    echo "用法: $0 <tar包路径> [模块: all|backend|frontend|db]"
    exit 1
fi

TARBALL="$1"
MODULE="${2:-all}"

if [[ ! -f "$TARBALL" ]]; then
    err "文件不存在: $TARBALL"
    exit 1
fi

if [[ "$MODULE" != "all" && "$MODULE" != "backend" && "$MODULE" != "frontend" && "$MODULE" != "db" ]]; then
    err "无效模块: $MODULE (可选: all, backend, frontend, db)"
    exit 1
fi

# --- 前置检查 ---
log "前置检查..."
for d in "$DEPLOY_DIR" "$NGINX_DIR"; do
    if [[ ! -d "$d" ]]; then
        err "目录不存在: $d"
        exit 1
    fi
done
if [[ ! -x "$MYSQL" ]]; then
    err "MySQL 客户端不存在: $MYSQL"
    exit 1
fi

# 检测 DB 凭据（优先环境变量，其次 application.yml）
DB_HOST="${APP_DB_HOST:-127.0.0.1}"
DB_PORT="${APP_DB_PORT:-3306}"
DB_USER="${APP_DB_USERNAME:-root}"
DB_PASS="${APP_DB_PASSWORD:-}"

if [[ -z "$DB_PASS" && -f "$DEPLOY_DIR/backend/application.yml" ]]; then
    DB_PASS=$(grep -A1 'password:' "$DEPLOY_DIR/backend/application.yml" | tail -1 | sed 's/.*password: *//;s/ *$//' | tr -d '"')
fi

MYSQL_CMD="$MYSQL -h$DB_HOST -P$DB_PORT -u$DB_USER"
if [[ -n "$DB_PASS" ]]; then
    MYSQL_CMD="$MYSQL_CMD -p$DB_PASS"
fi
MYSQL_CMD="$MYSQL_CMD $DB_NAME"

# --- 解压 ---
TMPDIR=$(mktemp -d /tmp/deploy-XXXXXX)
trap 'rm -rf "$TMPDIR"' EXIT
log "解压到 $TMPDIR ..."
tar -xzf "$TARBALL" -C "$TMPDIR"

# --- 备份（按模块分别备份） ---
TIMESTAMP=$(date +%Y%m%d%H%M%S)

if [[ "$MODULE" == "all" || "$MODULE" == "backend" ]]; then
    if [[ -f "$DEPLOY_DIR/backend/"*.jar ]]; then
        log "备份旧后端..."
        mkdir -p "$BACKUP_DIR/$TIMESTAMP/backend"
        cp "$DEPLOY_DIR/backend/"*.jar "$BACKUP_DIR/$TIMESTAMP/backend/" 2>/dev/null || true
        cp "$DEPLOY_DIR/backend/application.yml" "$BACKUP_DIR/$TIMESTAMP/backend/" 2>/dev/null || true
    fi
fi

if [[ "$MODULE" == "all" || "$MODULE" == "frontend" ]]; then
    if [[ -d "$DEPLOY_DIR/frontend" ]]; then
        log "备份旧前端..."
        mkdir -p "$BACKUP_DIR/$TIMESTAMP"
        cp -r "$DEPLOY_DIR/frontend" "$BACKUP_DIR/$TIMESTAMP/frontend"
    fi
fi

# --- DB 迁移 ---
if [[ "$MODULE" == "all" || "$MODULE" == "db" ]]; then
    if [[ -d "$TMPDIR/db" ]]; then
        SQL_FILES=$(find "$TMPDIR/db" -name "*.sql" -type f | sort)
        if [[ -n "$SQL_FILES" ]]; then
            log "执行 DB 迁移..."
            for sql in $SQL_FILES; do
                log "  执行: $(basename "$sql")"
                if ! $MYSQL_CMD < "$sql" 2>&1 | tee -a "$LOG_FILE"; then
                    warn "  $(basename "$sql") 执行有报错，请检查是否为重复执行导致的（可忽略）"
                fi
            done
        else
            log "无 DB 迁移脚本，跳过"
        fi
    fi
fi

# --- 部署后端 ---
if [[ "$MODULE" == "all" || "$MODULE" == "backend" ]]; then
    if [[ -d "$TMPDIR/backend" ]]; then
        log "停止旧后端..."
        systemctl stop api-gateway 2>/dev/null || true
        systemctl stop community-service 2>/dev/null || true
        systemctl stop infra-portal 2>/dev/null || true
        sleep 3

        log "部署新后端..."
        mkdir -p "$DEPLOY_DIR/backend"
        rm -f "$DEPLOY_DIR/backend/"*.jar
        cp "$TMPDIR/backend/"*.jar "$DEPLOY_DIR/backend/"

        if compgen -G "$TMPDIR/systemd/*.service" > /dev/null; then
            cp "$TMPDIR/systemd/"*.service /etc/systemd/system/
        fi

        # 配置文件：首次部署复制 example，已有则保留
        if [[ ! -f "$DEPLOY_DIR/backend/application.yml" ]]; then
            if [[ -f "$TMPDIR/backend/application.yml.example" ]]; then
                cp "$TMPDIR/backend/application.yml.example" "$DEPLOY_DIR/backend/application.yml"
                warn "已创建 application.yml，请检查数据库密码等配置"
            fi
        else
            log "  保留已有 application.yml"
        fi
        if [[ ! -f "$DEPLOY_DIR/backend/community-application.yml" && -f "$TMPDIR/backend/community-application.yml.example" ]]; then
            cp "$TMPDIR/backend/community-application.yml.example" "$DEPLOY_DIR/backend/community-application.yml"
            warn "已创建 community-application.yml，请检查数据库密码等配置"
        fi

        # 确保日志目录存在
        mkdir -p "$DEPLOY_DIR/logs"

        log "启动 app、community-service 与 api-gateway..."
        systemctl daemon-reload
        systemctl start infra-portal
        systemctl start community-service

        # app 先在 8081 就绪，再启动默认静态路由到 app 的网关
        log "等待 app 启动..."
        for i in $(seq 1 30); do
            if curl -sf http://localhost:8081/api/public/config > /dev/null 2>&1; then
                log "app 启动成功"
                break
            fi
            if [[ $i -eq 30 ]]; then
                err "app 启动超时，请检查日志: $DEPLOY_DIR/logs/infra-portal.log"
            fi
            sleep 2
        done

        log "等待 community-service 启动..."
        for i in $(seq 1 30); do
            if curl -sf "http://localhost:8082/api/forum/posts?page=0&size=1" > /dev/null 2>&1; then
                log "community-service 启动成功"
                break
            fi
            if [[ $i -eq 30 ]]; then
                err "community-service 启动超时，请检查日志: $DEPLOY_DIR/logs/community-service.log"
            fi
            sleep 2
        done

        systemctl start api-gateway
        log "等待 api-gateway 启动..."
        for i in $(seq 1 30); do
            if curl -sf http://localhost:8080/api/public/config > /dev/null 2>&1; then
                log "api-gateway 启动成功"
                break
            fi
            if [[ $i -eq 30 ]]; then
                err "api-gateway 启动超时，请检查日志: $DEPLOY_DIR/logs/api-gateway.log"
            fi
            sleep 2
        done
    fi
fi

# --- 部署前端 ---
if [[ "$MODULE" == "all" || "$MODULE" == "frontend" ]]; then
    if [[ -d "$TMPDIR/frontend" ]]; then
        log "部署前端..."
        rm -rf "$DEPLOY_DIR/frontend"
        cp -r "$TMPDIR/frontend" "$DEPLOY_DIR/frontend"
        log "前端部署完成"

        # 重载 nginx
        NGINX_BIN="$NGINX_DIR/sbin/nginx"
        if [[ -x "$NGINX_BIN" ]]; then
            log "重载 nginx..."
            "$NGINX_BIN" -t && "$NGINX_BIN" -s reload
        else
            warn "nginx 未找到: $NGINX_BIN，跳过重载"
        fi
    fi
fi

# --- 完成 ---
log "=========================================="
log "部署完成: $(basename "$TARBALL")"
log "模块: $MODULE"
log "日志: $LOG_FILE"
log "备份: $BACKUP_DIR/$TIMESTAMP/"
log "=========================================="
