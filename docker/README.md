# Docker 与 Compose 操作手册

本文只说明容器、服务、volume、构建、重启和日志。管理员与完整恢复流程分别见：

- [超级管理员指南](../docs/super-admin-guide.md)
- [完整备份与恢复](../docs/system-backup-restore.md)
- [Linux 生产部署](../docs/linux-production-deployment.md)

## 五个服务

| Compose 服务 | 容器内端口 | 作用 |
| --- | ---: | --- |
| `mysql` | 3306 | MySQL 8.4 应用数据库 |
| `backend` | 8080 | Spring Boot API |
| `frontend` | 80 | Vue 静态页面和 `/api` 反向代理 |
| `paddleocr` | 8866 | OCR 服务 |
| `libreoffice` | 3000 | Gotenberg/LibreOffice PDF 转换 |

不要依赖固定容器名；使用 `docker compose ... ps/exec/logs` 按服务名操作。

## 持久化 volumes

| Volume | 内容 | 备份要求 |
| --- | --- | --- |
| `mysql-data` | 业务数据库 | 完整备份包含逻辑 SQL |
| `backend-data` | 密文 uploads、导出结果、AI 主密钥、附件加密主密钥、恢复助手暂存包 | 必须与数据库属于同一恢复点；暂存包按 TTL 清理 |
| `paddleocr_models` | 可重新下载的 OCR 模型缓存 | 不进入迁移备份 |

只备份 MySQL 无法恢复附件，也无法解密数据库中的教师 AI 配置。

backend 的 `/app/tmp` 是独立 tmpfs，不属于 `backend-data`。Spring multipart 与 OCR 短生命周期
明文使用 `/app/tmp/academic-profile`；Compose 使用 `nosuid,nodev,noexec`，并将 tmpfs 所有者设为
镜像内的 `app` 用户（UID/GID 999），目录权限为 `0700`。应用尽量将文件设为 `0600` 并在完成或
失败后清理。不要把该目录改为持久化 volume。

CCF JSON 管理员上传也使用该 tmpfs，默认最多 5 MiB。Compose 不下载或捆绑第三方完整 CCF 数据；
具体导入和许可边界见[期刊与会议智能评级](../docs/publication-venue-ranking.md)。

## 配置和启动

```bash
cp .env.example .env
# 编辑 .env，设置随机数据库密码和 APP_JWT_SECRET

docker compose --profile full config --quiet
docker compose --profile full up -d --build
docker compose --profile full ps
```

AI API Key 不写入 `.env`，由每位教师登录后在“AI 模型设置”中配置。

## 构建和重建

```bash
# 构建全部应用镜像
docker compose --profile full build backend frontend

# 只重建后端
docker compose --profile full build backend
docker compose --profile full up -d --no-deps backend

# 只重建前端
docker compose --profile full build frontend
docker compose --profile full up -d --no-deps frontend
```

重建容器不会删除 named volumes。升级前仍应创建并下载完整备份。

## 状态、重启和日志

```bash
docker compose --profile full ps
docker compose --profile full restart backend frontend
docker compose --profile full logs --tail 100 backend frontend
docker compose --profile full logs -f backend
```

停止并保留容器和数据：

```bash
docker compose --profile full stop
```

再次启动：

```bash
docker compose --profile full start
```

## 健康检查

- MySQL：应用账户可以响应 `mysqladmin ping`。
- Backend：`/api/health` 返回成功。
- Frontend：Nginx `/healthz` 返回成功。
- PaddleOCR：OpenAPI 端点可访问。
- LibreOffice：转换服务健康端点可访问。

PaddleOCR 镜像在构建阶段下载并预置运行所需模型；首次挂载空的 `paddleocr_models` 命名卷时，
Docker 会用镜像内模型初始化该卷，启动阶段不应依赖外部模型站点。Compose 仍保留 15 分钟宽限期，
恢复脚本最多等待 30 分钟；持续 unhealthy 时查看对应服务日志，不要通过删除 volume 规避问题。

## 明确禁止

除非正在销毁已精确确认的专用测试环境，否则不要执行：

```text
docker compose down -v
docker volume prune
docker system prune --volumes
```

离线恢复脚本只允许操作部署者显式提供并确认的 Compose 项目。故障注入只能运行于项目名以
`restore-test-` 开头的隔离验收项目，不能指向默认项目或真实 volumes。
