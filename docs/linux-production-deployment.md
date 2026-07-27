# Linux 生产部署说明

本文说明如何在 Linux 服务器上运行当前的完整 Docker Compose 系统。它面向新服务器部署，不包含把既有 Windows 数据迁移到服务器的操作；数据迁移必须先单独备份并验证。

## 1. 当前项目在 Linux 上如何运行

不需要修改 Java、Vue、Dockerfile 或 `docker-compose.yml`。当前完整 Compose 会启动五个 Linux 容器：

- `mysql`：业务数据。
- `paddleocr`：图片和扫描 PDF 的 OCR。
- `libreoffice`：DOCX 转 PDF。
- `backend`：Spring Boot API、文件和业务逻辑。
- `frontend`：Vue 静态页面，以及转发 `/api` 的 Nginx。

浏览器访问、API 调用和容器间通信已经由现有配置处理：

```text
浏览器
  -> 宿主机 HTTPS 反向代理
  -> 127.0.0.1:8088 的 frontend 容器
  -> /api 转发到 backend 容器
  -> mysql、paddleocr、libreoffice 容器
```

`docker-compose.yml` 将 MySQL、Frontend、PaddleOCR 和 LibreOffice 的宿主机端口都绑定为 `127.0.0.1`。这表示它们只能由服务器本机访问，不能被公网直接扫描或连接。生产部署应保留这个边界，不要为了方便把端口改成 `0.0.0.0`。

## 2. 部署前条件

建议使用受支持的 x86_64 Linux 服务器，并在部署前确认：

```bash
uname -m
docker version
docker compose version
```

当前文档以 `x86_64` 为目标。若服务器是 ARM 架构，尤其要先在测试环境完整构建和启动 PaddleOCR，再安排正式部署。

服务器需要：

- 已安装并启用 Docker Engine 与 Docker Compose v2 插件。
- 一个专用于部署的普通 Linux 账号，且该账号可以执行 `docker` 命令。
- 足够的磁盘空间和内存。首次构建会下载 Java、Node、MySQL、PaddleOCR 模型和 LibreOffice 镜像；现有项目手册建议至少准备 8 GB 内存，OCR 负载较高时应按实际监控结果扩容。
- 若对公网提供服务：已解析到服务器的正式域名，以及允许 TCP `80`、`443` 的安全组和防火墙规则。

不要对公网开放 Docker API、MySQL `3306`、PaddleOCR `8866`、LibreOffice `3000` 或应用的 `8088`。SSH 管理端口也应限制为可信管理员来源。

## 3. 获取代码并保护部署目录

以下命令以部署账号执行。把 `/srv` 换成你实际使用的受控目录；不要把仓库放到临时目录或个人下载目录。

```bash
sudo mkdir -p /srv/academic-profile
sudo chown "$USER":"$USER" /srv/academic-profile
git clone https://github.com/pohanngame/Academic-Management-System.git /srv/academic-profile
cd /srv/academic-profile
```

仓库中没有真实密码和 API Key。部署账号只应拥有自己的部署目录权限，不应把 `.env`、备份或 Docker volume 复制到 Git 仓库。

## 4. 创建生产环境变量

`.env.example` 只提供变量名和示例值，不能直接用于生产。首次部署时创建仅由部署账号读取的 `.env`：

```bash
cd /srv/academic-profile
umask 077
cp .env.example .env
chmod 600 .env
```

编辑 `.env`，至少替换下面的值：

| 变量 | 生产要求 | 原因 |
|---|---|---|
| `MYSQL_PASSWORD` | 使用独立的高强度密码 | backend 连接业务数据库。 |
| `MYSQL_ROOT_PASSWORD` | 使用与普通账号不同的高强度密码 | 保护 MySQL 管理员账号。 |
| `APP_JWT_SECRET` | 使用至少 32 个随机字符，部署后不要随意更换 | 用于验证登录令牌；更换后现有登录会失效。 |

可以使用受信任的密码管理器生成密钥。若服务器安装了 OpenSSL，也可生成 JWT 密钥：

```bash
openssl rand -hex 48
```

将生成结果写入 `.env` 的 `APP_JWT_SECRET`。不要把真实 `.env` 发到聊天、工单、日志或截图中。`chmod 600` 的作用是阻止同一台服务器上的其他普通账号读取数据库密码和 JWT 密钥。

第三方 AI 的服务商、Base URL、模型名称和 API Key 不再写入 `.env`。系统启动后，每位教师登录并在“AI 模型设置”中独立保存和测试自己的配置。数据库只保存 API Key 密文；真实 Key 不会回显。

完整 Compose 模式会在容器内强制使用 `mysql`、`paddleocr` 和 `libreoffice` 服务名，因此不需要把 `DB_URL`、`OCR_BASE_URL` 或 `DOCUMENT_CONVERSION_BASE_URL` 改成服务器 IP。

## 5. 首次构建和启动

在项目根目录执行：

```bash
cd /srv/academic-profile
docker compose --profile full up -d --build
docker compose --profile full ps
```

第一个命令构建 backend、frontend、PaddleOCR 和 LibreOffice 镜像，并后台启动五个服务。第二个命令确认状态。首次启动 PaddleOCR 会下载模型，健康检查允许较长启动时间；不要因为仍处于 `starting` 就重复删除或重建数据卷。

当 `mysql`、`paddleocr`、`libreoffice`、`backend` 和 `frontend` 都显示 `healthy` 后，在服务器本机验证：

```bash
curl --fail http://127.0.0.1:8088/healthz
curl --fail http://127.0.0.1:8088/api/health
```

两个命令都返回成功，才继续配置公网入口。`8088` 只用于本机反向代理和本机健康检查，不是公网访问地址。

首次使用 AI 时，教师应登录系统，打开“AI 模型设置”，填写可信的 HTTPS OpenAI-compatible 地址、实际模型名称和自己的 API Key，保存后再执行连接测试。连接测试可能产生极少量调用费用。

首次 `SUPER_ADMIN` 不会自动创建，也没有公网初始化接口。按
[超级管理员指南](super-admin-guide.md) 的一次性 bootstrap 流程，只在部署终端临时导出变量，
使用 `docker compose run -e` 显式传给 one-off backend 容器，完成后立即 `unset`。普通注册始终是
`TEACHER`；第二名管理员由已登录超级管理员在系统管理页提升。

## 6. 配置 HTTPS 公网入口

推荐在宿主机使用独立的 HTTPS 反向代理。下面以 Caddy 为例，因为它可在域名正确解析、`80/443` 可访问时自动申请和续期证书。先按照 Caddy 官方文档为你的 Linux 发行版安装服务，再把站点配置中的域名替换为自己的正式域名：

```caddyfile
example.edu {
    reverse_proxy 127.0.0.1:8088
}
```

加载配置后，从外部访问 `https://example.edu`。反向代理只需要把整个站点转发到 `127.0.0.1:8088`：前端容器已经提供页面、SPA 路由回退和 `/api` 转发，因此不应额外公开 backend 或 Swagger。

如果使用宿主机 Nginx 或其他反向代理，原则相同：监听公网 `80/443`，终止 HTTPS，再代理到 `127.0.0.1:8088`。反向代理与 `frontend` 容器内的 Nginx 是两个不同层次的服务，前者负责公网 TLS，后者负责应用页面和 API 同源转发。

没有正式域名时，不要通过修改 Compose 的端口绑定临时公开系统。内网部署也应使用宿主机反向代理并把防火墙规则限制到指定内网网段。

## 7. 重启、日志与升级

所有服务均配置了 `restart: unless-stopped`。启用 Docker 服务后，服务器重启时已创建且未手动停止的容器会自动恢复：

```bash
sudo systemctl enable --now docker
```

常用运行检查：

```bash
cd /srv/academic-profile
docker compose --profile full ps
docker compose --profile full logs --tail 100 frontend backend
docker compose --profile full logs -f paddleocr
```

升级前先完成备份并记录当前提交：

```bash
cd /srv/academic-profile
git rev-parse --short HEAD
git pull --ff-only
docker compose --profile full up -d --build
docker compose --profile full ps
```

`git pull --ff-only` 只接受线性更新，避免在服务器上自动产生合并提交。升级后应重新执行本机健康检查和一次真实业务验收。若升级失败，先停止继续操作，使用升级前记录的提交在测试环境验证回退方案，不要删除 volume 来解决问题。

## 8. 数据与备份

系统的持久化数据位于 Docker named volume：

- MySQL 数据：`mysql-data`。
- OCR 模型：`paddleocr_models`。
- 上传、导出、临时文件和 AI 配置加密主密钥：`backend-data`。

Docker 会给实际 volume 名加入 Compose 项目前缀。先查看真实名称：

```bash
docker volume ls
```

定期由 `SUPER_ADMIN` 在 HTTPS 页面“系统管理 → 数据备份”重新输入当前密码并创建完整迁移包。
包同时包含 MySQL 逻辑备份、uploads 和同一恢复点的 `/app/data/secrets/ai-config.key`。仅有 MySQL
备份既不能恢复附件，也不能解密教师 AI 配置。`.apbackup` 不含 `.env` 或 JWT Secret，并且自身
没有额外加密，下载后必须立即移到受控存储。

`APP_BACKUP_MAXIMUM_DOWNLOAD_BYTES` 控制管理页可下载备份的最大字节数，默认
`536870912`（512 MiB）。后端流式输出且不一次性载入 JVM，但当前浏览器端使用 Blob，接近上限时
可能占用较多浏览器内存；超过上限的临时包会在返回文件正文前删除。SHA-256 只用于完整性检查，
不提供来源认证，恢复前必须确认备份来自可信系统并经受控渠道传输。

恢复时必须恢复原加密主密钥。密钥丢失或不匹配时，系统会返回“AI 配置无法解密，请恢复加密密钥备份”，不会静默覆盖或删除数据库密文。不要在真实数据环境中通过删除密钥文件测试这一故障；应复制备份到隔离环境，保留原文件，再用不匹配密钥启动副本验证安全失败。备份应保存在服务器之外的受控位置，并定期在隔离测试环境中演练恢复。没有恢复验证的备份不能视为可用备份。

迁移到新服务器前先运行 `scripts/restore-backup.sh --backup <受控路径> --verify-only`，再按照
[完整迁移备份与恢复](system-backup-restore.md) 使用独立 Compose project name 恢复。在线页面
没有覆盖恢复按钮；覆盖现有目标必须显式 `--force`、输入确认短语，且脚本会先创建安全副本。失败
时保留/恢复原数据并返回非零退出码。

严禁把备份文件、`.env` 或 Docker volume 提交 Git。正常维护时不要执行以下命令：

```bash
docker compose down -v
docker volume prune
docker system prune --volumes
```

这些命令可能删除数据库、上传附件或 OCR 模型。需要恢复或迁移时，应先暂停写入、保留原始数据、在测试环境验证后再执行生产操作。

## 9. 故障定位顺序

1. 先执行 `docker compose --profile full ps`，确认五个服务是否 healthy。
2. 若 frontend 异常，查看 `frontend` 日志并检查 `http://127.0.0.1:8088/healthz`。
3. 若 API 异常，查看 `backend` 日志并检查 `http://127.0.0.1:8088/api/health`。
4. 若 OCR 或 PDF 转换异常，查看对应服务日志，不要先修改超时、端口或删除 volume。
5. 容器都健康但公网不可访问时，检查域名解析、宿主机防火墙、安全组、HTTPS 证书和宿主机反向代理，而不是开放内部容器端口。

## 10. 部署边界

本文完成的是单台 Linux 服务器上的 Compose 生产部署。高可用、多机扩容、独立数据库、对象存储、集中日志、WAF、限流和灾备需要基于实际访问量、学校网络策略和数据合规要求另行设计，不能在未评估的情况下直接叠加到当前配置。
