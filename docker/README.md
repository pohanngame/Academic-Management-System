# Docker 操作手册

## 十分钟快速启动

所有命令都在项目根目录执行，也就是 `docker-compose.yml` 所在目录。

```powershell
# 第一次使用：创建本机配置
Copy-Item .env.example .env

# 编辑 .env，替换数据库密码、root 密码和至少 32 字符的 JWT Secret

# 构建并启动完整系统
docker compose --profile full up -d --build

# 查看五个服务状态
docker compose --profile full ps
```

全部服务显示 `healthy` 后，浏览器打开 <http://127.0.0.1:8088>。

## 一、五个服务的用途

| Compose 服务 | 容器名称 | 作用 |
|---|---|---|
| `mysql` | `academic-profile-mysql` | 保存账号、教师资料和所有业务数据 |
| `paddleocr` | `academic-profile-paddleocr` | 识别图片和扫描 PDF |
| `libreoffice` | `academic-profile-libreoffice` | Gotenberg 调用 LibreOffice 把 DOCX 转为 PDF |
| `backend` | `academic-profile-backend` | Spring Boot API、JWT、文件和业务逻辑 |
| `frontend` | `academic-profile-frontend` | Nginx 提供 Vue 页面并把 `/api` 转给 backend |

## 二、服务调用关系

```text
浏览器
  |
  v
frontend :80 (宿主机 127.0.0.1:8088)
  |-- 静态 Vue 页面
  `-- /api/* ---------------------> backend :8080
                                      |---> mysql :3306
                                      |---> paddleocr :8866
                                      `---> libreoffice :3000
```

backend 不直接暴露宿主机端口。这样浏览器只访问 frontend，API 与页面同源，减少端口暴露和跨域配置。

## 三、profile 的区别

Docker Compose profile 用来选择可选服务。

| 命令 | 启动内容 |
|---|---|
| `docker compose up -d` | 只启动 MySQL |
| `docker compose --profile ocr up -d` | MySQL + PaddleOCR |
| `docker compose --profile document up -d` | MySQL + Gotenberg/LibreOffice |
| `docker compose --profile full up -d --build` | 五个服务全部启动 |

`mysql` 没有 profile，所以任何模式都会包含它。`paddleocr` 同时属于 `ocr` 和 `full`；`libreoffice` 同时属于 `document` 和 `full`；backend/frontend 只属于 `full`。

## 四、端口和容器内部地址

### 宿主机端口

| 服务 | 默认宿主机地址 |
|---|---|
| MySQL | `127.0.0.1:3306` |
| Frontend | `127.0.0.1:8088` |
| PaddleOCR | `127.0.0.1:8866` |
| Gotenberg | `127.0.0.1:3000` |
| Backend | 不直接发布宿主机端口 |

端口只绑定 `127.0.0.1`，局域网其他电脑默认不能直接访问。

### Compose 网络内部地址

- MySQL：`mysql:3306`
- Backend：`backend:8080`
- PaddleOCR：`http://paddleocr:8866`
- Gotenberg：`http://libreoffice:3000`
- Frontend：`frontend:80`

Windows 主机运行的 backend 应使用 `127.0.0.1`，容器中的 backend 才能使用这些 Compose 服务名，两种模式不要混用。

## 五、三个 volume

Named volume 是 Docker 管理的持久化目录。当前有：

- `mysql-data`：MySQL 数据。
- `paddleocr_models`：PaddleOCR 官方模型。
- `backend-data`：backend 的 `/app/data`，包括上传、导出和临时目录。

重启或重新创建容器不会自动删除 volume。旧的主机目录 `backend/data` 不会自动迁移进 `backend-data`。

不要执行：

```powershell
docker compose down -v
docker volume prune
docker system prune --volumes
```

这些命令可能删除数据库、上传文件和 OCR 模型。

## 六、启动、停止、重启、状态和日志

```powershell
# 执行目录：项目根目录
# 构建并启动完整系统
docker compose --profile full up -d --build

# 停止五个服务但保留容器和 volume
docker compose --profile full stop

# 再次启动已有容器
docker compose --profile full up -d

# 查看状态
docker compose --profile full ps

# 查看 backend 和 frontend 最近 100 行日志
docker compose --profile full logs --tail 100 backend frontend

# 持续查看某个服务日志，Ctrl+C 只退出日志查看
docker compose --profile full logs -f paddleocr
```

只重启 backend：

```powershell
docker compose restart backend
```

只重启 frontend：

```powershell
docker compose restart frontend
```

## 七、只构建或重建 backend

修改 Java 代码后：

```powershell
# 只构建 backend 镜像
docker compose --profile full build backend

# 只重建 backend 容器，不重建依赖服务
docker compose --profile full up -d --no-deps --force-recreate backend

# 等待并查看 backend 状态
docker compose --profile full ps backend
```

backend 使用 Maven `3.9.9` + Java 17 多阶段构建，运行镜像为固定 Temurin Java 17，并使用非 root 用户运行。

## 八、只构建或重建 frontend

修改 Vue 或 Nginx 后：

```powershell
# 只构建 frontend 镜像
docker compose --profile full build frontend

# 只重建 frontend 容器
docker compose --profile full up -d --no-deps --force-recreate frontend

# 等待并查看 frontend 状态
docker compose --profile full ps frontend
```

frontend 使用固定 Node 22 镜像执行 `npm ci` 和 `npm run build`，再把 `dist` 复制到固定 Nginx Alpine 镜像。

## 九、PaddleOCR 首次启动

PaddleOCR 服务使用 PaddlePaddle `3.3.1` 和 PaddleOCR `3.7.0`。第一次启动空的 `paddleocr_models` volume 时，会下载以下模型并保存到 `/root/.paddlex`：

- 文档方向模型。
- 文档矫正模型。
- 文本行方向模型。
- 文字检测模型。
- 中英文文字识别模型。

首次下载可能持续数分钟甚至更久。后续容器重新创建会复用 volume，不应完整重复下载。

查看进度：

```powershell
docker compose --profile full logs -f paddleocr
```

## 十、Gotenberg/LibreOffice

`libreoffice` 服务基于固定 digest 的 Gotenberg LibreOffice 镜像，并安装 Noto CJK 字体。它接收 backend 生成的 DOCX，转换为 PDF 后立即返回。

默认本机健康地址：<http://127.0.0.1:3000/health>。

PDF 转换默认有请求大小、响应大小、队列和超时限制。backend 的总等待时间为 70 秒，Nginx `/api` 代理读取超时为 75 秒。

## 十一、Frontend Nginx

Nginx 完成两件事：

1. 使用 `try_files $uri $uri/ /index.html` 实现 SPA fallback。直接刷新 `/login`、`/dashboard` 或 `/profiles/...` 时仍返回 Vue 页面。
2. 把原始 `/api` 路径代理到 `http://backend:8080`，不会错误删除 `/api` 前缀。

Nginx 保留 Authorization 和常用转发头，允许最大 `20m` 请求体。`/healthz` 由 Nginx 自己返回 200，不经过 backend。

## 十二、构建代理变量

Docker Desktop 可能自动注入代理。项目提供：

- `BUILD_HTTP_PROXY`、`BUILD_HTTPS_PROXY`、`BUILD_ALL_PROXY`：backend、frontend 和 LibreOffice 构建使用。
- `OCR_HTTP_PROXY`、`OCR_HTTPS_PROXY`、`OCR_ALL_PROXY`：PaddleOCR 构建和运行使用。

当前已验证环境可以将它们留空，从而覆盖无效代理。需要代理时由部署者在 `.env` 中提供，但不要提交包含用户名或密码的代理地址。

Frontend healthcheck 会只在检查进程中临时清除六个代理变量，避免访问 `127.0.0.1/healthz` 时被错误发送给外部代理。它不会改变 Nginx 其他运行行为。

## 十三、安全注入 AI_API_KEY

`AI_API_KEY` 不写入 `.env.example`、Dockerfile、镜像、README 或 Git。需要 AI 时，在执行 Compose 的普通 PowerShell 中安全输入：

```powershell
$secureKey = Read-Host "请输入本次运行使用的 AI API Key" -AsSecureString
$env:AI_API_KEY = [System.Net.NetworkCredential]::new("", $secureKey).Password
$env:AI_MODEL = "<供应商账号确认可用的模型名>"

docker compose --profile full up -d --build
```

没有 Key 时完整系统仍能启动，只有 AI 任务创建会失败。

## 十四、五个 healthcheck 的含义

| 服务 | 检查内容 |
|---|---|
| MySQL | 使用应用数据库账号执行 `mysqladmin ping` |
| PaddleOCR | 容器内访问 FastAPI `/openapi.json` |
| LibreOffice | 容器内访问 Gotenberg `/health` |
| Backend | 容器内访问 `/api/health` |
| Frontend | 容器内访问 Nginx `/healthz` |

backend 会等待 MySQL、PaddleOCR、LibreOffice healthy 后启动；frontend 会等待 backend healthy 后启动。

## 十五、常见 Docker 故障

### 无法连接 Docker Engine

启动 Docker Desktop，然后执行 `docker version`。如果只有受限沙箱账号报权限错误，请改用正常登录用户的普通 PowerShell，不要随意修改 `.docker/config.json` ACL。

### 基础镜像拉取超时

一次只拉取一个镜像，记录真实错误。不要未经确认切换第三方镜像源或随意更换版本。

### PaddleOCR 一直 starting

查看日志是否仍在下载模型。健康检查允许较长启动时间。若日志有 traceback，再按最后一个明确错误处理。

### Frontend unhealthy，但页面能打开

检查 healthcheck 日志和 Docker 注入代理。当前配置已经在 healthcheck 中临时清除代理；重新构建并只重建 frontend 后应恢复 healthy。

### 页面打开但 API 返回错误

检查：

```powershell
docker compose --profile full ps
docker compose --profile full logs --tail 100 frontend backend
```

分别访问 `/healthz` 和 `/api/health`，判断问题在 Nginx 还是 backend。

### 修改 MySQL 密码后仍不能登录数据库

MySQL volume 已初始化后，修改 `.env` 不会自动修改数据库内部已有账号密码。不要删除 volume 解决此问题，应先备份并制定账号密码变更步骤。

### Backend 重建后找不到旧主机附件

完整 Compose 使用 `backend-data`，不会自动读取或迁移 Windows 主机的 `backend/data`。迁移前必须备份，并单独验证文件元数据与实际文件一一对应。
