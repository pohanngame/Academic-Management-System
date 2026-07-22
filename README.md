# 个人学术信息管理与简介展示系统

## 十分钟快速启动

下面的步骤适用于 Windows 电脑。所有命令都在项目根目录执行，也就是能看到 `docker-compose.yml` 的目录。

### 1. 准备软件

1. 安装并启动 Docker Desktop。
2. 打开普通 PowerShell，不要使用来源不明的管理员脚本。
3. 确认 Docker Engine（真正运行容器的后台服务）可用：

```powershell
docker version
docker compose version
```

两条命令都能正常显示版本后再继续。第一次构建需要联网下载 Java、Node、PaddleOCR 模型和 LibreOffice 镜像，可能需要较长时间和数 GB 磁盘空间。

### 2. 创建本地配置

```powershell
# 执行目录：项目根目录
# 用途：复制一份仅供本机使用的环境变量配置
Copy-Item .env.example .env
```

用文本编辑器打开 `.env`，至少修改：

- `MYSQL_PASSWORD`：系统连接数据库使用的密码。
- `MYSQL_ROOT_PASSWORD`：MySQL 管理员密码，应与普通数据库密码不同。
- `APP_JWT_SECRET`：登录令牌签名密钥，至少使用 32 个随机字符。
- `AI_MODEL`：必须根据实际 AI 供应商账号显式填写并确认有效。

`.env` 只能留在本机，不能提交到 Git。

### 3. 启动完整系统

```powershell
# 执行目录：项目根目录
# 用途：构建并启动 MySQL、OCR、PDF 转换、后端和前端
docker compose --profile full up -d --build

# 执行目录：项目根目录
# 用途：查看五个服务的运行和健康状态
docker compose --profile full ps
```

等待 `mysql`、`paddleocr`、`libreoffice`、`backend`、`frontend` 全部显示 `healthy`。

### 4. 打开系统

- 系统入口：<http://127.0.0.1:8088>
- 前端健康检查：<http://127.0.0.1:8088/healthz>
- 后端健康检查：<http://127.0.0.1:8088/api/health>

如果修改了 `.env` 中的 `FRONTEND_PORT`，请把地址中的 `8088` 换成实际端口。

## 一、系统简介

这是一个供多名教师分别维护个人学术档案的系统。每位教师只能管理自己的资料，可以录入个人信息、学术成果和附件，也可以生成公开主页、导入 BibTeX、执行 OCR 识别、导出 Word/Excel，以及使用第三方 AI 生成待确认的材料草稿。

系统采用前后端分离结构：

- 后端：Spring Boot 3.3.7、Java 17、MyBatis-Plus、Spring Security、JWT。
- 前端：Vue 3、Vite、Element Plus、Pinia、Axios。
- 数据库：MySQL 8.4，表结构由 Flyway V1-V9 管理。
- 辅助服务：PaddleOCR 3.7.0、Gotenberg/LibreOffice。
- 部署：Docker Compose。

## 二、已实现功能

- 教师注册、登录、退出和 JWT 登录状态。
- 教师资料、头像、电话、办公室、邮箱、简介和公开路径管理。
- 学历、教学方向、研究方向、专业服务、工作经历管理。
- 科研项目、授课记录、论文、专利、证书管理。
- PDF、JPG、JPEG、PNG、WEBP 附件上传、本人下载和逻辑删除。
- 公开教师主页、字段公开开关和分块公开控制。
- Word/Excel 普通导出，可选择模块、字段、顺序并保存常用导出模板。
- BibTeX 文本和 `.bib` 文件批量导入，可预览、修改、忽略和确认。
- PDFBox 可复制文字提取，以及 PaddleOCR 图片和扫描 PDF 识别。
- AI 草稿生成、人工编辑、人工确认和历史记录。
- 只包含一个 `{{aiContent}}` 的 DOCX Word 模板。
- 确认后的 AI 内容导出 Word，并可选转换为 PDF。

## 三、项目目录结构

```text
.
|-- backend/                 Spring Boot 后端、Flyway 迁移和后端 Dockerfile
|   |-- src/                 Java 源码、配置和数据库迁移
|   `-- data/                主机开发模式的本地文件目录，不会自动迁移到 Docker volume
|-- frontend/                Vue 3 前端、Nginx 配置和前端 Dockerfile
|-- docker/                  PaddleOCR、Gotenberg/LibreOffice 辅助服务
|-- docs/                    文档导航
|-- data/                    若本机存在，属于运行数据，不应提交
|-- docker-compose.yml       五个服务、profile、端口和 volume 配置
|-- .env.example             环境变量示例，不包含真实密钥
|-- .env                     本机实际配置，不可提交 Git
`-- README.md                本使用手册
```

## 四、安装前准备

### Docker Desktop

Docker Desktop 用来在 Windows 上运行 Linux 容器。安装后必须先启动它，等待界面显示 Docker Engine 已运行，再执行项目命令。

建议至少准备：

- 可用的互联网连接。
- 8 GB 以上内存，运行 OCR 时建议更多。
- 数 GB 可用磁盘空间。
- 未被占用的 `3306`、`8088`、`8866` 和 `3000` 端口。

首次启动 PaddleOCR 会下载模型。看到持续下载、文件数量或网络流量变化时通常不是卡死，请先等待；只有出现明确 traceback、超时或容器退出时才按错误处理。

## 五、第一次配置 `.env`

先执行：

```powershell
# 执行目录：项目根目录
Copy-Item .env.example .env
```

常用配置说明：

| 变量 | 作用 | 注意事项 |
|---|---|---|
| `MYSQL_DATABASE` | 数据库名称 | 默认 `academic_profile` |
| `MYSQL_USER` | 应用使用的普通数据库账号 | 不建议让应用使用 root |
| `MYSQL_PASSWORD` | 普通数据库账号密码 | 必须替换示例值 |
| `MYSQL_ROOT_PASSWORD` | MySQL root 密码 | 必须替换示例值 |
| `MYSQL_PORT` | MySQL 暴露到本机的端口 | 默认 `3306` |
| `FRONTEND_PORT` | 浏览器访问系统的端口 | 默认 `8088` |
| `APP_JWT_SECRET` | JWT 登录令牌签名密钥 | 至少 32 个随机字符 |
| `APP_JWT_EXPIRATION_MINUTES` | 登录令牌有效分钟数 | 默认 `120` |
| `AI_PROVIDER` | AI 供应商标识 | 当前示例为 `deepseek` |
| `AI_BASE_URL` | OpenAI-compatible API 根地址 | 客户端会追加 `/chat/completions` |
| `AI_MODEL` | AI 模型名称 | 启动前必须显式设置并确认有效 |
| `AI_MAX_TOKENS` | 单次生成最大输出 Token | 默认 `1024` |
| `AI_MAX_PROMPT_CHARS` | 发送前允许的最大 Prompt 字符数 | 不是精确 Token 或费用上限 |
| `OCR_PORT` | PaddleOCR 本机端口 | 默认 `8866` |
| `OCR_TIMEOUT_SECONDS` | 后端等待 OCR 的秒数 | 默认 `60` |
| `DOCUMENT_CONVERSION_TIMEOUT_SECONDS` | 后端等待 PDF 转换的秒数 | 默认 `70` |
| `LIBREOFFICE_PORT` | Gotenberg 本机端口 | 默认 `3000` |

完整 Compose 模式会在容器内部强制启用 OCR 和 PDF 转换，并使用容器服务名互相访问。`.env.example` 中的 `OCR_ENABLED=false` 和 `DOCUMENT_CONVERSION_ENABLED=false` 主要用于 Windows 主机开发模式。

### 已知的 AI 模型默认值差异

当前真实配置存在差异：

- `.env.example` 和 `application.yml` 默认值为 `deepseek-v4-flash`。
- `docker-compose.yml` 的 fallback 为 `deepseek-chat`。

文档不判断哪一个一定适用于你的账号。部署者必须在运行环境中显式设置 `AI_MODEL`，并以供应商账号当前可用模型为准。

## 六、安全设置 AI API Key

AI Key 不是系统自带的。开发者和甲方应使用各自账号的 Key，甲方生产部署必须使用甲方自己的 Key。

没有 `AI_API_KEY` 时，系统、数据库、OCR、导出等功能仍可启动；创建 AI 任务时会受控失败。

不要把 Key 写入 README、`.env.example`、Dockerfile、日志或 Git。完整 Compose 模式下，可以在启动命令所在的普通 PowerShell 中临时输入：

```powershell
# 执行目录：项目根目录
# 用途：不在命令历史中直接写出明文 Key
$secureKey = Read-Host "请输入本次运行使用的 AI API Key" -AsSecureString
$env:AI_API_KEY = [System.Net.NetworkCredential]::new("", $secureKey).Password

# 必须显式填写供应商账号确认可用的模型名
$env:AI_MODEL = "<供应商账号确认可用的模型名>"

docker compose --profile full up -d --build
```

启动命令结束后可清理当前 PowerShell 中的临时 Key：

```powershell
Remove-Item Env:AI_API_KEY -ErrorAction SilentlyContinue
Remove-Variable secureKey -ErrorAction SilentlyContinue
```

这不会删除已经启动的容器环境。需要更换 Key 时，应在新的安全会话中设置后重新创建 backend 容器。

## 七、一条命令启动完整系统

```powershell
# 执行目录：项目根目录
docker compose --profile full up -d --build

# 查看当前状态
docker compose --profile full ps
```

第一次构建可能下载 Maven/Java、Node、Nginx、PaddlePaddle、PaddleOCR 模型以及 Gotenberg/LibreOffice，耗时取决于网络和磁盘速度。

## 八、五个服务与健康状态

| 服务 | 用途 | 正常状态 |
|---|---|---|
| `mysql` | 保存账号、教师资料和业务数据 | `healthy` |
| `paddleocr` | 识别图片和扫描 PDF | `healthy` |
| `libreoffice` | 通过 Gotenberg 将 DOCX 转成 PDF | `healthy` |
| `backend` | Spring Boot API、认证和业务逻辑 | `healthy` |
| `frontend` | Nginx 提供 Vue 页面并代理 `/api` | `healthy` |

健康检查地址：

- <http://127.0.0.1:8088/healthz>：Nginx 前端健康检查。
- <http://127.0.0.1:8088/api/health>：通过 Nginx 访问后端健康检查。

## 九、第一次使用顺序

1. 浏览器打开 <http://127.0.0.1:8088>。
2. 进入注册页，创建教师账号。
3. 登录后台。
4. 在“教师资料”中填写姓名、职称、部门、电话、办公室、邮箱和个人简介。
5. 上传头像。
6. 设置允许公开的字段，填写唯一的公开路径 `slug`，并打开公开状态。
7. 添加学历、教学方向、研究方向、专业服务和工作经历。
8. 添加科研项目、授课记录、论文、专利和证书。
9. 在论文、专利或证书的编辑区上传附件。
10. 使用页面上的公开主页链接检查展示结果。

附件原文件默认不在公开主页提供下载，只允许登录后的所属教师访问。

## 十、BibTeX 导入

进入后台“BibTeX 导入”页面，可以：

1. 粘贴包含一条或多条记录的 BibTeX 文本，或上传 `.bib` 文件。
2. 系统使用 BibTeX 解析库逐条解析，不会只读取第一条。
3. 在候选列表中检查标题、作者、期刊、会议、年份、DOI 等字段。
4. 修改候选记录，或忽略不需要的记录。
5. 查看 DOI 或标题等产生的可能重复提示。
6. 人工确认后再写入正式论文/学术成果表。

重复提示不会静默覆盖旧论文；教师可以在明确知情后决定是否继续导入。

## 十一、OCR 使用方法

OCR（光学字符识别）会把图片中的文字转换为可编辑文本。当前处理方式：

| 文件类型 | 识别方式 |
|---|---|
| 普通图片 | `PADDLE_OCR` |
| 有足够可复制文字的 PDF | `PDF_TEXT`，由 PDFBox 提取 |
| 扫描 PDF | `PADDLE_OCR`，逐页转图后识别 |

使用顺序：

1. 先创建论文、专利或证书记录。
2. 给该记录上传图片或 PDF 附件。
3. 在 OCR 页面选择本人附件并创建任务。
4. 查看识别文本和候选字段。
5. 人工修改、忽略或确认。
6. 只有人工确认后才写入正式成果，OCR 不会直接覆盖正式资料。

首次启动 PaddleOCR 会下载官方模型并存入 `paddleocr_models` volume。扫描件清晰度、倾斜、盖章和复杂表格会影响准确率。

## 十二、AI 与 Word 模板

### 无模板模式

输入文字要求，选择需要使用的教师资料模块，系统把结构化资料发送给配置的第三方 AI API。AI 返回内容先保存为草稿，教师必须人工编辑和确认。

### DOCX 模板模式

- 只支持 `.docx`。
- 模板必须只包含一个 `{{aiContent}}`。
- 不支持 `.doc`、`.docm`、多个占位符或任意复杂 Word 模板。
- 当前不承诺页眉、页脚、文本框、批注、修订内容和复杂域中的占位符。
- Word 二进制文件不会直接发送给 AI，但系统提取的模板结构文字和所选教师资料会进入 Prompt。

AI 输出始终是草稿。未确认的草稿不能导出；确认后可以导出 Word，如果 `libreoffice` 可用，也可以导出 PDF。AI 生成结果不会自动覆盖正式教师资料。

## 十三、普通 Word/Excel 导出

后台“导出”页面支持：

- 选择 Word 或 Excel。
- 勾选需要的资料模块。
- 勾选模块内字段。
- 调整字段顺序。
- 保存为常用导出模板。
- 下次复用或删除常用模板。

Excel 按模块分 Sheet。普通导出使用确定性数据，不经过 AI，只导出当前登录教师自己的正式资料。

## 十四、停止、重新启动和查看日志

```powershell
# 执行目录：项目根目录
# 停止 full profile 的容器，但保留容器和 volume 数据
docker compose --profile full stop

# 重新启动已有容器
docker compose --profile full up -d

# 查看五个服务状态
docker compose --profile full ps

# 查看后端和前端最近 100 行日志
docker compose --profile full logs --tail 100 backend frontend
```

如果需要持续查看日志，在命令末尾加 `-f`；按 `Ctrl+C` 只会退出日志查看，不会停止容器。

## 十五、数据持久化

Docker named volume（由 Docker 管理的持久化目录）有三个：

- `mysql-data`：保存 MySQL 数据库。
- `paddleocr_models`：保存 PaddleOCR 已下载模型。
- `backend-data`：挂载到 backend 的 `/app/data`，保存上传、导出和临时目录。

只停止或重新创建容器不会删除这些 volume。旧的主机目录 `backend/data` 不会自动迁移到 `backend-data`，如需迁移必须单独制定、备份和验证方案。

## 十六、危险命令警告

正常使用时不要执行：

```powershell
docker compose down -v
docker volume prune
docker system prune --volumes
```

这些命令可能删除 MySQL 数据、上传文件或 OCR 模型。除非已经完成备份并明确要重置数据，否则不要运行。

## 十七、常见问题

### Docker Desktop 未启动

现象：无法连接 `docker_engine`。先启动 Docker Desktop，等待 Engine 可用，再执行 `docker version`。

### 端口被占用

修改 `.env` 中对应端口，例如 `MYSQL_PORT`、`FRONTEND_PORT`、`OCR_PORT` 或 `LIBREOFFICE_PORT`，然后重新启动相关服务。已经存在的 MySQL volume 不会因为修改 `.env` 密码而自动修改数据库中的旧账号密码。

### 服务一直显示 `starting`

查看服务日志：

```powershell
# 执行目录：项目根目录
docker compose --profile full logs --tail 100 <服务名>
```

PaddleOCR 首次启动的健康检查等待时间较长，因为需要下载和初始化模型。

### PaddleOCR 下载很慢

先观察日志、下载文件数、网络和磁盘变化。项目已经提供 `PIP_DEFAULT_TIMEOUT`、重试次数和模型 volume。不要因为短时间没有输出就删除容器或 volume。

### Docker 代理导致下载或 healthcheck 失败

`.env.example` 提供 `OCR_HTTP_PROXY` 和 `BUILD_HTTP_PROXY` 等变量。当前已验证的本机环境可以把这些值留空，以覆盖无效代理。不要把包含账号密码的代理地址提交到 Git。

### AI 提示未配置

说明 backend 没有收到 `AI_API_KEY`，或模型配置无效。系统其他功能仍可使用。请按“安全设置 AI API Key”章节在当前 PowerShell 中注入，并显式确认 `AI_MODEL`。

### OCR 服务不可连接

检查：

```powershell
docker compose --profile full ps paddleocr
docker compose --profile full logs --tail 100 paddleocr
```

确认容器 healthy，完整 Compose 内后端地址应为 `http://paddleocr:8866`。

### PDF 转换不可用

检查 `libreoffice` 是否 healthy，并查看日志。完整 Compose 内后端地址应为 `http://libreoffice:3000`。

### 页面能打开，但 API 不通

先访问 `/healthz` 和 `/api/health`，再检查 `frontend` 与 `backend` 日志。Nginx 会保留 `/api` 路径并代理到 backend。

### 修改前端后页面没有更新

只重建 frontend：

```powershell
# 执行目录：项目根目录
docker compose --profile full build frontend
docker compose --profile full up -d --no-deps --force-recreate frontend
```

### 修改后端后没有更新

只重建 backend：

```powershell
# 执行目录：项目根目录
docker compose --profile full build backend
docker compose --profile full up -d --no-deps --force-recreate backend
```

## 十八、Windows 主机开发模式（进阶）

主机开发模式和完整 Compose 模式的服务地址不能混用：

| 运行位置 | MySQL | PaddleOCR | Gotenberg |
|---|---|---|---|
| Windows 主机上的 backend | `127.0.0.1:3306` | `http://127.0.0.1:8866` | `http://127.0.0.1:3000` |
| Compose 内的 backend | `mysql:3306` | `http://paddleocr:8866` | `http://libreoffice:3000` |

主机开发启动示例：

```powershell
# 执行目录：项目根目录
docker compose up -d mysql

# 执行目录：backend
$env:DB_URL="jdbc:mysql://127.0.0.1:3306/academic_profile?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
$env:DB_USERNAME="academic_profile_user"
$env:DB_PASSWORD="<本机数据库密码>"
$env:APP_JWT_SECRET="<至少32个随机字符>"
mvn spring-boot:run
```

前端开发：

```powershell
# 执行目录：frontend
npm ci
npm run dev
```

访问 <http://127.0.0.1:5173>，Vite 会把 `/api` 代理到主机 backend 的 `8080`。

## 十九、生产交付边界

当前配置适合本机或可信内网 Docker 部署，所有宿主机端口只绑定 `127.0.0.1`。

正式公网投产前还需要单独完成：

- HTTPS 和正式域名。
- 防火墙、反向代理和访问控制。
- MySQL、上传文件和配置的定期备份与恢复演练。
- 日志收集、监控、告警和容量管理。
- 正式密钥管理，不把密钥放入仓库或普通日志。
- 根据真实并发量制定扩容、限流和第三方 API 费用控制方案。

更多代码导航和 Docker 说明见 [docs/README.md](docs/README.md) 与 [docker/README.md](docker/README.md)。
