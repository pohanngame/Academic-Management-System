# 运维与恢复脚本

## 生产运维脚本

- `verify-backup.py`：校验 `.apbackup` 格式、必需文件、schema 版本、路径安全和 SHA-256。
- `restore-backup.sh`：Linux 离线恢复入口。生产恢复只接受可信渠道取得的备份包。

只校验、不写入任何目标：

```bash
python3 scripts/verify-backup.py /secure/path/backup.apbackup --max-schema 11
bash scripts/restore-backup.sh --backup /secure/path/backup.apbackup --verify-only
```

恢复参数和安全边界见 [完整备份与恢复手册](../docs/system-backup-restore.md)。

## 测试专用资产

`tests/` 包含：

- `test_verify_backup.py`
- `test-restore-docker.sh`
- `restore-test-compose.yml`

运行快速测试：

```bash
python3 -m unittest scripts/tests/test_verify_backup.py
bash -n scripts/restore-backup.sh
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
