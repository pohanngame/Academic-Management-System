# 学术档案管理系统

面向高校教师的个人学术档案、成果、附件、OCR、文档导出和 AI 辅助材料生成系统。项目采用
Spring Boot、Vue 3、MySQL 8.4 和 Docker Compose，教师数据及第三方 AI 配置按登录用户隔离。

## 功能摘要

- 教师注册、登录和个人资料维护
- 项目、论文、专利、著作、奖励等成果管理
- 附件上传、下载、BibTeX 导入和 OCR
- Word/Excel 导出及 PDF 转换
- 每位教师独立配置 OpenAI-compatible AI 服务
- `TEACHER` / `SUPER_ADMIN` 固定角色
- 管理员概览、用户角色/状态管理和审计日志
- 完整迁移备份：数据库、uploads 和 AI 加密主密钥
- Linux 离线校验、恢复和故障回滚工具

## 快速启动

### 1. 准备环境

- Docker Desktop 或 Linux Docker Engine
- Docker Compose v2
- Git

### 2. 创建本机配置

```powershell
Copy-Item .env.example .env
```

编辑 `.env`，至少设置数据库密码、root 密码和长度不少于 32 字符的随机 JWT Secret。不要提交
`.env`。AI API Key 不写入 `.env`，由教师登录后在页面中配置。

### 3. 构建并启动

```bash
docker compose --profile full config --quiet
docker compose --profile full up -d --build
docker compose --profile full ps
```

五个服务均显示 `healthy` 后，打开 `http://localhost:8088`。首次 PaddleOCR 模型初始化可能需要较长时间。

禁止执行会删除数据卷的命令，例如：

```text
docker compose down -v
docker volume prune
docker system prune --volumes
```

## TEACHER 与 SUPER_ADMIN

普通注册始终创建 `TEACHER`。教师只能访问自己的档案、附件、AI 配置和生成任务。

`SUPER_ADMIN` 可以访问系统管理控制台，查看概览、管理账户角色/状态、查看管理员审计并创建完整备份。
管理员仍不能查看密码哈希、教师 AI Key 明文、冒充教师或绕过教师数据隔离。

系统不会生成默认管理员，也不会把首位注册用户自动提升。首位管理员必须由部署者在服务器终端使用
一次性 bootstrap 创建或提升。完整命令和保护规则见
[超级管理员指南](docs/super-admin-guide.md)。

## AI 模型配置

1. 注册并登录教师账户。
2. 打开“AI 模型设置”。
3. 填写服务商、HTTPS Base URL、模型名称和 API Key。
4. 保存并测试连接。

API Key 使用 AES-256-GCM 加密后存入数据库，真实 Key 不会回显。主密钥位于
`backend-data:/app/data/secrets/ai-config.key`，数据库备份和主密钥必须属于同一恢复点。

## 完整备份

只有 `SUPER_ADMIN` 可以在管理页创建和下载完整备份，创建前需要重新输入当前密码。

备份包含：

- 应用数据库逻辑备份
- `/app/data/uploads`
- `/app/data/secrets/ai-config.key`
- 带版本和 SHA-256 的 `manifest.json`

备份不包含 `.env`、JWT Secret、数据库密码、镜像、缓存、OCR 模型、target、dist 或 node_modules。
`.apbackup` 未加密且包含全体用户数据和 AI 解密主密钥，应只保存到受控位置。SHA-256 只能发现内容
损坏，不能证明备份来源。

后端流式发送文件，不把完整包一次性读入 JVM；当前浏览器使用 Blob，接近默认 512 MiB 上限时可能
占用较多浏览器内存。超过 `APP_BACKUP_MAXIMUM_DOWNLOAD_BYTES` 的包会在响应前删除。

恢复不提供网页 API，只能由拥有目标服务器和 Docker 权限的运维人员离线执行。详见
[完整备份与恢复手册](docs/system-backup-restore.md)。

## 项目目录

```text
backend/       Spring Boot 后端和 Flyway V1–V11
frontend/      Vue 3 + Element Plus
docker/        OCR、PDF 等服务及 Docker 操作说明
docs/          管理员、部署、备份恢复文档
scripts/       生产校验/恢复脚本
scripts/tests/ 隔离恢复自动化测试资产
```

## 常用开发验证

```bash
cd backend
mvn test

cd ../frontend
npm ci
npm run build

cd ..
python3 -m unittest scripts/tests/test_verify_backup.py
bash -n scripts/restore-backup.sh
bash -n scripts/tests/test-restore-docker.sh
docker compose --profile full config --quiet
```

完整恢复矩阵只能在专用 `restore-test-*` Compose 项目和临时 volumes 上运行，不能指向真实项目。

## 文档

- [文档索引](docs/README.md)
- [Linux 生产部署](docs/linux-production-deployment.md)
- [超级管理员指南](docs/super-admin-guide.md)
- [完整备份与恢复](docs/system-backup-restore.md)
- [Docker 操作手册](docker/README.md)
- [脚本与隔离测试](scripts/README.md)
