# 数据库初始化

本目录存储系统初始化 SQL 和种子数据，用于数据库恢复或新环境部署。

## 文件说明

| 文件 | 说明 |
|------|------|
| `init.sql` | 完整建表语句（DDL），包含所有表结构 |
| `seed.sql` | 种子数据（DML），包含管理员账号、角色、软件分类、中间件命令等初始数据 |
| `restore.sh` | 一键恢复脚本 |

## 快速恢复

```bash
# 一键恢复数据库（会清空现有数据）
bash db/restore.sh

# 或手动执行
mysql --default-character-set=utf8mb4 -u root -p middleware_resource_manager < db/init.sql
mysql --default-character-set=utf8mb4 -u root -p middleware_resource_manager < db/seed.sql
```

## 仅恢复种子数据

如果表结构未变，只需恢复种子数据：

```bash
mysql --default-character-set=utf8mb4 -u root -p middleware_resource_manager < db/seed.sql
```

## 默认账号

| 账号 | 密码 | 角色 |
|------|------|------|
| sysadmin | admin123 | 系统管理员 |
| mwadmin | admin123 | 中间件管理岗 |
| dbadmin | admin123 | 数据库管理岗 |
| hostadmin | admin123 | 主机管理岗 |
| netadmin | admin123 | 网络管理岗 |
| secadmin | admin123 | 网络安全岗 |
| devmgr | admin123 | 开发经理 |
| opsmgr | admin123 | 运维经理 |

## 注意事项

- `init.sql` 会删除并重建所有表（`DROP TABLE IF EXISTS`）
- `seed.sql` 使用 `LOCK/UNLOCK TABLES` 保证数据一致性
- 恢复前请确认数据库名正确（默认 `middleware_resource_manager`）
- 环境变量 `APP_DB_HOST`、`APP_DB_PORT`、`APP_DB_NAME`、`APP_DB_USERNAME`、`APP_DB_PASSWORD` 可覆盖默认连接
