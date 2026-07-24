

- docker compose

```yaml
services:
  gitlab-runner-tlb:
    image: gitlab/gitlab-runner:alpine
    # image: gitlab/gitlab-runner:alpine-v14.0.1
    container_name: gitlab-runner-tlb
    restart: unless-stopped
    environment:
      TZ: Asia/Shanghai
    volumes:
      - ./config:/etc/gitlab-runner:Z
      - ./cache:/cache:Z
      - /var/run/docker.sock:/var/run/docker.sock
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
  name = "TLB"
  url = "https://gitlab-tlb.shcj-s.com/"
  id = 2
  token = "<GitLab Runner authentication token>"
  token_obtained_at = 2026-07-23T07:13:21Z
  token_expires_at = 0001-01-01T00:00:00Z
  executor = "docker"
  request_concurrency = 2
  [runners.docker]
    tls_verify = false
    # CI Job 通过 Docker Socket 构建镜像并执行 docker compose。
    image = "docker:27-cli"
    pull_policy = "if-not-present"
    allowed_pull_policies = ["always", "if-not-present"]
    privileged = false
    dns = ["223.5.5.5"]
    disable_entrypoint_overwrite = false
    oom_kill_disable = false
    disable_cache = false
    volumes = ["/cache", "/var/run/docker.sock:/var/run/docker.sock"]
    volume_keep = false
    shm_size = 0
    network_mtu = 0
```
