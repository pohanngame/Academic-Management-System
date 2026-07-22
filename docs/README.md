# 文档导航

## 十分钟快速了解项目

第一次接触项目时，建议按下面顺序阅读：

1. 先看根目录 [README.md](../README.md)，完成配置、启动和首次使用。
2. 需要操作 Docker 时看 [docker/README.md](../docker/README.md)。
3. 需要修改后端时看 `backend/src/main/java/com/example/academicprofile/`。
4. 需要修改前端时看 `frontend/src/`。
5. 需要了解数据库时只读查看 `backend/src/main/resources/db/migration/`。

本目录用于集中存放需求、架构、接口、部署和验收类文档。根 README 是面向使用者的主手册，本 README 是面向维护者的导航。

## 项目代码在哪里

### 后端

`backend/` 是 Spring Boot 后端，主要目录：

```text
backend/
|-- src/main/java/com/example/academicprofile/
|   |-- auth/           注册、登录和当前用户
|   |-- security/       JWT 和登录身份
|   |-- teacher/        教师基础资料
|   |-- profileblock/   学历、方向、服务和经历
|   |-- achievement/    项目、授课、论文、专利和证书
|   |-- file/           文件元数据、上传、下载和权限
|   |-- exporting/      普通 Word/Excel 导出
|   |-- publicprofile/  公开教师主页
|   |-- bibtex/         BibTeX 批量导入
|   |-- ocr/            OCR 任务、候选和确认
|   |-- ai/             AI 调用、任务和草稿
|   |-- word/           DOCX 模板校验和渲染
|   `-- pdf/            DOCX 转 PDF 客户端
|-- src/main/resources/application.yml
|-- src/main/resources/db/migration/
|-- pom.xml
`-- Dockerfile
```

### 前端

`frontend/` 是 Vue 3 前端，主要目录：

```text
frontend/src/
|-- views/       登录、资料、成果、导出、OCR、AI 和公开页
|-- services/    Axios API 调用
|-- stores/      Pinia 登录状态
|-- router/      Vue Router 路由和登录守卫
|-- layouts/     后台整体布局
`-- assets/      全局样式
```

前端容器配置位于：

- `frontend/Dockerfile`
- `frontend/nginx.conf`
- `frontend/.dockerignore`

### Docker 辅助服务

`docker/` 保存辅助服务：

- `docker/paddleocr/`：PaddleOCR HTTP 服务。
- `docker/libreoffice/`：Gotenberg/LibreOffice PDF 转换服务。
- 根目录 `docker-compose.yml`：五个服务、profile、端口和 volume。

## Flyway V1-V9 概览

Flyway（数据库迁移工具）会按版本顺序自动创建和调整表结构。

| 版本 | 作用 |
|---|---|
| V1 | 创建用户和教师资料表 |
| V2 | 扩展教师资料并创建学历、方向、服务和经历表 |
| V3 | 创建项目、授课、论文、专利和证书表 |
| V4 | 创建统一文件元数据表 |
| V5 | 创建普通导出模板表 |
| V6 | 创建 BibTeX 导入任务和候选表 |
| V7 | 创建 OCR 任务和结果表 |
| V8 | 创建 AI 生成任务和结果表 |
| V9 | 让 AI 任务关联 Word 模板文件 |

已经执行过的 V1-V9 不得修改、重命名或删除。数据库结构需要变化时，应新增下一版本迁移，并先评估已有数据兼容性。

## Swagger 接口文档

Swagger UI（浏览器中的后端接口说明）由 Springdoc 提供，后端路径是：

```text
http://127.0.0.1:8080/swagger-ui.html
```

使用前提：Spring Boot 以 Windows 主机开发模式运行，并直接监听 `8080`。

当前完整 Compose 模式只通过 frontend Nginx 对外开放 `/api`，backend 不发布宿主机端口，Nginx 也没有代理 `/swagger-ui` 和 `/v3/api-docs`。因此完整 Compose 默认没有外部 Swagger 入口。这是当前部署边界，不应为了看 Swagger 临时放宽公网访问。

## 配置文件位置

| 文件 | 用途 |
|---|---|
| `.env.example` | 本地环境变量示例，不含真实密钥 |
| `.env` | 本机实际 Compose 配置，不可提交 Git |
| `docker-compose.yml` | 服务、profile、端口、依赖和 volume |
| `backend/src/main/resources/application.yml` | Spring Boot 默认配置及环境变量映射 |
| `backend/pom.xml` | Java 版本和 Maven 依赖 |
| `frontend/package.json` | 前端依赖和 npm 命令 |
| `frontend/vite.config.js` | 主机开发服务器及 `/api` 代理 |
| `frontend/nginx.conf` | 容器内 SPA fallback 和 `/api` 反向代理 |

## 数据与敏感信息边界

以下内容不能写入 README、源码或 Git：

- `.env`。
- 数据库真实密码和 `APP_JWT_SECRET`。
- `AI_API_KEY`、Token 和 Authorization 头。
- 教师真实隐私资料或真实附件。
- `backend/data`、Docker volume 和数据库文件。
- `backend/target`、`frontend/dist`、`node_modules` 和日志。

文件上传根目录、导出目录和临时目录都必须配置化，不能写死开发者电脑的绝对路径。

## 已知限制

- Word 模板只支持 `.docx` 和一个 `{{aiContent}}`。
- 不支持 `.doc`、`.docm`、多个占位符或任意复杂 Word 模板。
- AI 草稿必须人工确认，模型输出不保证绝对准确。
- OCR 准确率会受到扫描质量、倾斜、盖章和复杂表格影响。
- 完整 Compose 默认不对外开放 Swagger。
- 旧主机目录 `backend/data` 不会自动迁移到 `backend-data` volume。
- 当前 Compose 更适合本机或可信内网；公网投产仍需 HTTPS、备份、监控和正式密钥管理。
- `.env.example`/`application.yml` 与 Compose 的 AI 模型 fallback 不同，部署者必须显式设置并验证 `AI_MODEL`。

## 修改功能时先看哪里

| 修改目标 | 优先查看目录或文件 |
|---|---|
| 登录和权限 | `auth/`、`security/`、`frontend/src/stores/auth.js` |
| 教师资料 | `teacher/`、`profileblock/`、对应前端 views/services |
| 学术成果 | `achievement/`、`AcademicRecordListView.vue` |
| 文件与附件 | `file/`、`frontend/src/services/files.js` |
| 普通导出 | `exporting/`、`frontend/src/views/export/` |
| BibTeX | `bibtex/`、`frontend/src/views/bibtex/` |
| OCR | `ocr/`、`docker/paddleocr/`、`frontend/src/views/ocr/` |
| AI | `ai/`、`word/`、`pdf/`、`frontend/src/views/ai/` |
| 公开主页 | `publicprofile/`、`frontend/src/views/public/` |
| Docker 部署 | `docker-compose.yml`、各 Dockerfile、`frontend/nginx.conf` |

修改前先确认所有后台数据接口仍从 JWT 登录态获取 `teacher_id`，不要相信前端传来的归属 ID。文件、任务、模板和导出数据都必须继续按教师隔离。

## 后续文档建议

后续可以在本目录新增：

- `PROJECT_STATUS.md`：阶段状态和剩余工作。
- 架构说明：模块关系、调用链和数据流。
- 交付验收记录：环境、命令、版本和验收结果。

本轮不创建这些新文件。
