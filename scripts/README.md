# 运维与恢复脚本

## 生产运维脚本

- `verify-backup.py`：校验 `.apbackup` 格式、必需文件、schema 版本、路径安全和 SHA-256。
- `restore-backup.sh`：Linux 离线恢复入口。生产恢复只接受可信渠道取得的备份包。
- `restore-staged-backup.sh`：恢复助手薄包装器。按 UUID 从既有 backend-data 暂存目录流式复制包，
  先校验，再调用 `restore-backup.sh`；它不包含另一套恢复实现。

只校验、不写入任何目标：

```bash
python3 scripts/verify-backup.py /secure/path/backup.apbackup
bash scripts/restore-backup.sh --backup /secure/path/backup.apbackup --verify-only
bash scripts/restore-staged-backup.sh --id <UUID> --verify-only
```

`restore-staged-backup.sh` 参数：

- `--id UUID`：必填，管理页生成的任务 ID。
- `--project-dir DIR`：可选，默认当前目录；目录必须包含 Compose 文件。
- `--verify-only`：只复制并校验，不停止服务、不写数据库、不创建 volume。
- `--force`：原样传给 `restore-backup.sh`；不会跳过其人工确认。

正式成功后包和 sidecar 会从 restore-inbox 删除，失败时保留。包装器在宿主机使用 Docker CLI；
backend 本身没有 Docker Socket。不要在不可信项目目录运行，不要将 SHA-256 当作来源认证。

恢复参数和安全边界见 [完整备份与恢复手册](../docs/system-backup-restore.md)。

## 测试专用资产

`tests/` 包含：

- `test_verify_backup.py`
- `test-restore-docker.sh`
- `test-restore-staged-backup.sh`
- `restore-test-compose.yml`

运行快速测试：

```bash
python3 -m unittest scripts/tests/test_verify_backup.py
bash -n scripts/restore-backup.sh
bash -n scripts/restore-staged-backup.sh
bash scripts/tests/test-restore-staged-backup.sh
bash -n scripts/tests/test-restore-docker.sh
```

运行完整隔离恢复矩阵：

```bash
bash scripts/tests/test-restore-docker.sh
```

完整矩阵会创建名称以 `restore-test-` 开头的临时 Compose 项目，并验证空恢复、覆盖恢复、恶意 SQL
隔离和故障回滚。禁止把测试 Compose 文件、`RESTORE_TEST_FAIL_AT` 或
`RESTORE_TEST_CONFIRMATION` 用于真实项目名、真实数据库或真实 volumes。脚本不会调用
`down -v`、`volume prune` 或系统级 volume 清理。

校验器支持 v1/v2。v2 必须包含 `secrets/file-encryption.key`；v1 恢复的历史明文附件需运行受控
迁移，不得直接标记为已加密。
