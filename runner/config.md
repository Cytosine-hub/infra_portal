

- docker compose

```yaml
services:
  gitlab-runner:
    image: gitlab/gitlab-runner:alpine
    # image: gitlab/gitlab-runner:alpine-v14.0.1
    container_name: gitlab-runner
    restart: unless-stopped
    environment:
      TZ: Asia/Shanghai
    volumes:
      - ./config:/etc/gitlab-runner:Z
      - ./cache:/cache:Z
      - /var/run/docker.sock:/var/run/docker.sock
      - /app:/app
```

- Register

```bash
docker run -it --rm --name gitlab-runner-int -v /app/gitlab-runner-tlb/config:/etc/gitlab-runner -v /var/run/docker.sock:/var/run/docker.sock gitlab/gitlab-runner:alpine register --url https://gitlab-tlb.shcj-s.com --token <runner-authentication-token>
```

- Config

```toml
concurrent = 1
check_interval = 0
shutdown_timeout = 0

[session_server]
  session_timeout = 1800

[[runners]]
  name = "TLZJJ"
  url = "https://gitlab-tlb.shcj-s.com/"
  id = 3
  token = "<GitLab Runner authentication token>"
  token_obtained_at = 2026-07-24T01:57:03Z
  token_expires_at = 0001-01-01T00:00:00Z
  executor = "docker"
  request_concurrency = 2
  [runners.docker]
    tls_verify = false
    image = "alpine"
    pull_policy = "if-not-present"
    allowed_pull_policies = ["always", "if-not-present"]
    privileged = false
    disable_entrypoint_overwrite = false
    oom_kill_disable = false
    # 显式绑定宿主机缓存目录，因此关闭 Runner 自动创建的 /cache 卷；
    # 两者同时启用会报 "volume for container path /cache is already defined"。
    disable_cache = true
    volumes = ["/var/run/docker.sock:/var/run/docker.sock", "/app:/app", "/opt/gitlab-runner/cache:/cache"]
    volume_keep = false
    shm_size = 0
    network_mtu = 0
```
