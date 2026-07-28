# 文档索引

## 推荐阅读顺序

1. [项目 README](../README.md)：项目概述、功能摘要和快速启动。
2. [Linux 生产部署](linux-production-deployment.md)：服务器配置、HTTPS、启动、升级和日常运维。
3. [超级管理员指南](super-admin-guide.md)：bootstrap、权限矩阵、保护规则和人工验收。
4. [完整备份与恢复](system-backup-restore.md)：备份安全边界、恢复助手、校验、离线恢复和隔离演练。
5. [Docker 操作手册](../docker/README.md)：Compose 服务、端口、volume、构建和容器运维。
6. [脚本说明](../scripts/README.md)：生产恢复脚本与测试专用资产。

## 文档用途

| 文档 | 适用对象 | 主要内容 |
| --- | --- | --- |
| `README.md` | 初次使用者 | 快速启动、功能和角色概览 |
| `linux-production-deployment.md` | 部署运维 | Linux、HTTPS、环境变量、升级 |
| `super-admin-guide.md` | SUPER_ADMIN/运维 | 首位管理员、用户管理、权限验收 |
| `system-backup-restore.md` | SUPER_ADMIN/恢复运维 | 导出、恢复助手、verify-only、离线恢复和回滚 |
| `docker/README.md` | 开发与运维 | 五服务、volume、构建、重启、日志 |
| `scripts/README.md` | 恢复演练人员 | 脚本参数、测试目录、隔离矩阵 |

## 当前数据和安全边界

- 角色固定为 `TEACHER`、`SUPER_ADMIN`，没有动态 RBAC。
- 教师业务数据和 AI 配置继续以当前 JWT 对应的 `teacherId` 隔离。
- 管理员不能查看密码哈希、教师 AI Key 明文，也不能冒充教师。
- 完整备份包含数据库、uploads 和 AI 加密主密钥，因此是高度敏感且未加密的文件。
- SHA-256 只能校验内容完整性，不能认证备份来源。
- 只恢复 MySQL 不足以恢复附件和教师 AI 配置。
- 不要执行 `docker compose down -v`、`docker volume prune` 或系统级 volume 清理。
