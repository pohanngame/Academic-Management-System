# 完整迁移备份与恢复

## 权限与安全边界

只有当前数据库中仍为 `ACTIVE SUPER_ADMIN` 的账户能查看备份记录、创建和下载备份。创建前后端
会使用 BCrypt 重新验证当前密码。未登录返回 401，教师返回 403。管理页的恢复助手只暂存文件和
生成命令，不提供在线覆盖恢复 API。完整覆盖恢复只能由拥有目标服务器 Docker 权限的运维人员离线执行，因为恢复会替换账户、JWT 状态、
数据库和文件目录。

`.apbackup` **没有额外加密**。它包含全体用户数据和私人附件，因此只能经 HTTPS 下载，随后立即
移到权限受控、加密的离线或对象存储。不要放入 Git、普通下载目录、聊天、工单或公共共享盘。
包内 SHA-256 可以发现意外损坏，但没有签名或 MAC，不能证明备份来源。攻击者能够同时修改内容和
manifest，因此只允许恢复来自可信系统、经受控渠道传输并由运维人员核对来源的备份。

后端流式输出备份，不会一次性将完整包载入 JVM。当前管理页面使用 Axios `responseType: 'blob'`，
浏览器会先构造完整 Blob；接近 `APP_BACKUP_MAXIMUM_DOWNLOAD_BYTES`（默认 512 MiB）时可能占用
较多浏览器内存，超过上限的包会在响应前删除。“一次性下载票据 + 浏览器原生下载”属于后续优化。

## 包格式 v2 与旧 v1

文件名为 `academic-profile-backup-YYYYMMDD-HHMMSS-SSS.apbackup`，本质是 ZIP，但只应由本项目
校验器处理：

```text
manifest.json
database.sql
uploads/**
secrets/ai-config.key
secrets/file-encryption.key
```

`manifest.json` 记录格式版本、创建时间和时区、应用版本/Git commit、Flyway schema 版本、
MySQL 版本、文件数量、是否包含 AI 主密钥，以及每个数据文件的大小和 SHA-256。数据库通过
`mysqldump --single-transaction --quick --hex-blob` 导出；密码只写入短生命周期 0600
defaults-extra-file，不出现在命令行或日志。

明确排除 `.env`、数据库密码、`APP_JWT_SECRET`、`target`、`dist`、`node_modules`、temp、
backups 自身、OCR 模型、Docker 镜像和缓存。新服务器必须使用自己的 `.env` 与 JWT Secret，
因此来源服务器的旧登录会话自然失效。

v2 将数据库、密文 uploads、AI 配置主密钥和附件加密主密钥放入同一恢复点。缺少任一主密钥时，
校验和恢复都会失败。v1 仍可校验与恢复；其 uploads 可能是历史明文，恢复后不得错误标记为已加密，
必须按[历史附件迁移流程](access-control-and-file-encryption.md#历史附件迁移)处理。

创建期间后端阻塞由本应用处理的写请求，先复制 uploads 和不可变主密钥，再执行 InnoDB 一致性
逻辑导出，最后在同一临时文件系统原子发布完整包。它不承诺抵抗绕过应用直接写数据库或 volume 的
外部进程；备份窗口内禁止这类操作。同一进程一次只允许一个备份。下载结束、取消或超时后临时包
会删除；进程重启也会清理残留临时包。

## 只校验，不写目标

在目标 Linux 服务器复制代码和备份后先运行：

```bash
cd /srv/academic-profile
python3 scripts/verify-backup.py /受控路径/backup.apbackup --max-schema 15

# 等价的恢复脚本只读入口
scripts/restore-backup.sh --backup /受控路径/backup.apbackup --verify-only
```

## 管理页恢复助手

“系统管理 → 数据备份 → 备份恢复助手”是半自动运维入口，不是网页一键恢复：

1. SUPER_ADMIN 选择可信来源的 `.apbackup`，重新输入当前密码并点击“上传恢复包”。
2. 后端流式写入 `backend-data:/app/data/restore-inbox`，过程中计算实际大小和 SHA-256。服务端只使用
   随机 UUID 文件名，先写 `.partial`，完成后原子发布包和 JSON sidecar。
3. 页面显示任务 ID、大小、SHA-256、上传/过期时间和“已暂存，等待服务器执行”。
4. 运维人员登录 Linux 服务器，在项目根目录复制执行：

   ```bash
   bash scripts/restore-staged-backup.sh --id <UUID> --verify-only
   ```

5. 校验通过后，空目标执行：

   ```bash
   bash scripts/restore-staged-backup.sh --id <UUID>
   ```

   非空目标必须显式增加 `--force`，并继续完成 `restore-backup.sh` 要求的人工确认短语：

   ```bash
   bash scripts/restore-staged-backup.sh --id <UUID> --force
   ```

上传成功只表示文件已暂存，不表示校验或恢复成功。SHA-256 只能发现损坏，不认证来源。后端容器没有
Docker Socket、MySQL root 密码或宿主机 Shell 权限；HTTP 请求也不会停止容器、导入 SQL 或切换目录。
包装脚本在宿主机解析实际 Compose project name，从既有 backend-data 卷流式复制包到权限受限的
`mktemp`，先调用 `verify-backup.py`，再调用现有 `restore-backup.sh`，不复制恢复逻辑。

暂存包默认最大 512 MiB（`APP_RESTORE_MAX_UPLOAD_BYTES`），默认 24 小时后过期
（`APP_RESTORE_STAGED_TTL`）。应用启动和定时任务会清理过期包、sidecar 及非活动 `.partial`。
恢复成功后包装脚本删除对应包和 sidecar；失败时保留供排查和重试。管理员也可在过期前重新验证密码
后删除。恢复期间页面可能短暂不可用。

校验器拒绝错误扩展名、非法 ZIP、重复路径、绝对路径、`..` 路径穿越、反斜杠路径、符号链接、
缺少 manifest/database/key、未知格式版本、过新的 schema、文件清单差异和 SHA-256/大小差异。
`--verify-only` 不启动容器、不创建 volume、不写数据库。

## 新服务器恢复

1. 在新服务器准备同版本或能识别备份 schema 的代码。
2. 创建目标服务器自己的 `.env`；不要复制来源 `.env` 或 JWT Secret。
3. 构建 full profile，确认项目目录是绝对、受控目录。
4. 先执行 `--verify-only`。
5. 可先用恢复助手上传并执行上一节的固定命令；也可直接使用受控路径中的包。直接恢复时使用一个
   明确且只属于目标的 Compose project name：

```bash
cd /srv/academic-profile
docker compose --profile full build backend frontend
scripts/restore-backup.sh \
  --backup /受控路径/academic-profile-backup-....apbackup \
  --project-dir /srv/academic-profile \
  --project-name academic-profile-new
```

脚本核对 Compose 配置、project name、数据库名和两个精确 volume 的 Compose labels；默认只接受
空数据库/新 volume。它先把 SQL 导入临时数据库验证，再停止 backend/frontend、在 volume 内
staging uploads/key、替换目标数据库和文件，设置 key 为 0600 与应用用户所有，启动 full profile，
等待 mysql/backend/frontend/paddleocr/libreoffice 五个服务 healthy，检查 Flyway 版本和关键文件，
最后删除临时明文目录。

导入 `database.sql` 时不会使用 MySQL root。脚本为临时验证库和正式目标库分别创建不可预测的
临时账户，仅授予对应单个数据库的权限，并通过 0600 的短生命周期 defaults-extra-file 认证。
root 只执行脚本生成且数据库名已通过白名单验证的建库、删库、建用户、单库授权和删用户语句。
临时账户与凭据文件在成功和失败路径都会清理。

## 覆盖、回滚与失败

目标存在数据时脚本默认拒绝。只有审核目标后显式添加 `--force`，并人工输入
`RESTORE <project-name>` 才会继续。覆盖前脚本在目标项目目录的 `restore-safety/<时间>/` 创建
权限受限的数据库和 backend-data 安全副本。任何数据库导入、文件切换、健康检查失败都会返回非零
退出码，并尝试恢复原数据库和文件；不要删除该 safety 目录，先查明原因并验证原系统。

恢复成功后：

```bash
docker compose --project-name academic-profile-new --profile full ps
curl --fail http://127.0.0.1:8088/healthz
curl --fail http://127.0.0.1:8088/api/health
```

再使用来源账户登录，核对角色/状态、两位教师隔离、附件 SHA-256、历史 AI 任务，并确认 AI 配置能
使用随包恢复的原 `ai-config.key` 解密 AI 配置，并使用 `file-encryption.key` 解密附件。来源环境
不应发生任何变化。

不要执行 `docker compose down -v`、`docker volume prune` 或 `docker system prune --volumes`。
只允许在名称经过核对的专用演练项目上清理临时资源。至少定期进行一次完全隔离的恢复演练；没有
实际恢复验证的包不能视为可用备份。

### 测试专用故障注入

`RESTORE_TEST_FAIL_AT` 只用于隔离恢复演练，默认关闭，不通过应用 API 暴露。设置后项目名必须以
`restore-test-` 开头。允许值为：

- `before_file_switch`
- `after_original_uploads_moved`
- `after_original_secrets_moved`
- `after_new_uploads_installed`
- `after_new_secrets_installed`
- `after_database_switch`
- `before_health_check`

生产恢复不得设置该变量。每个故障点都必须在专用 Linux Docker 环境验证数据库 sentinel、附件和
主密钥哈希、权限、临时账户及 stage/rollback 清理后，才能将恢复脚本判定为生产可用。

自动化验收可在同样的 `restore-test-*` 项目中通过 `RESTORE_TEST_CONFIRMATION` 提供覆盖确认短语；
生产项目会拒绝该变量，生产交互固定从终端 `/dev/tty` 读取。
