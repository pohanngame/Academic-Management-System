# TASK：课表公共空闲

契约：`.specs/timetable-PRD.md`、`.specs/timetable-API.yaml`、`.specs/timetable-SCHEMA.sql`。不改 V1–V17，不把课表写入 `teaching_course`。

- [ ] 任务 1：新增 `V17__create_timetable_snapshot.sql`，内容与 `.specs/timetable-SCHEMA.sql` 一致。验证：Flyway 能走完，已有迁移测试不失败。
- [ ] 任务 1b：把备份可接受的最高 Flyway 版本从 15 改为 17，且只保留 `scripts/verify-backup.py` 的 `DEFAULT_MAX_SCHEMA` 这一处数字。`restore-staged-backup.sh` 和 `scripts/tests/test-restore-docker.sh` 调用校验时不再传 `--max-schema`。同步 `docs/system-backup-restore.md`、`scripts/README.md` 和 `scripts/tests/test_verify_backup.py`。验证：`python3 -m unittest scripts/tests/test_verify_backup.py` 通过；schema 17 被默认接受，schema 18 仍被拒绝。
- [ ] 任务 1c：`scripts/restore-backup.sh` 不再写死 `MAX_SCHEMA=15`。默认上限改为运行 `python3 scripts/verify-backup.py --help` 时解析出的 `DEFAULT_MAX_SCHEMA`；`--max-schema` 仍可临时覆盖。第 33、347、541 行继续用这个值。若 `scripts/tests/test_verify_backup.py` 锁了旧默认值，同步改为“默认值来自 verify-backup.py”。验证：`bash -n scripts/restore-backup.sh` 通过；脚本中不再出现 `MAX_SCHEMA=15`；不传 `--max-schema` 时，17 被接受、18 被拒绝。
- [ ] 任务 2：完成映射的实体、Mapper 和三个管理接口，仅 `SUPER_ADMIN`。验证：重复姓名、重复教师、不存在教师都失败。
- [ ] 任务 3：实现 `POST /api/timetable/sync/captcha` 和 `POST /api/timetable/sync`。先判断验证码，密码按契约加密，再走教务票据并解析一个学期的 `#kbtable`。登录态不落库、不写日志。验证：验证码错误不留半批；`kbjcmsid` 用总表页已选项；43 列样例能解析出正确星期和节次；未映射姓名进入 `unmappedNames` 且不入库；响应无 Cookie。
- [ ] 任务 3d：修正 `kbjcmsid` 取值。有 `selected` 仍用该选项；没有 `selected` 且 `#kbjcmsid` 只有一个带 value 的选项时用该选项；有多个选项且都没有 `selected` 时仍报错，不取第一个，也不回退成 `0102`。验证：三种 HTML 样例分别覆盖已选、唯一选项、多选项无选中。不改登录、解析、V1–V18 和其余契约。
- [ ] 任务 3v：修正 Cookie 归属。没有 Domain 的 Set-Cookie 是 host-only，只归当前主机，并按 Path 分开保存；不能升到父域，也不能被同名父域 Cookie 覆盖。带 Domain 的 Cookie 仍按该域及其子域发送。验证：认证站 host-only 的 CASTGC 在后续访问 authserver.dgpt.edu.cn 时仍存在，访问 jw.dgpt.edu.cn 时不携带。不改 V1–V18、登录字段、课表解析、kbjcmsid 和 HTTPS 基址。
- [ ] 任务 3u：滑块轨迹改为认证页字段 `a`、`b`、`c`。`a` 是水平位移，`b` 是纵向位移，`c` 是距离上一个点的毫秒数；不得再提交 `x`、`y`、`t`。验证：前端测试断言轨迹点只含这三个字段，`npm test` 和 `npm run build` 通过。不改 V1–V18、登录字段、课表解析、`kbjcmsid` 和 HTTPS 基址。
- [ ] 任务 3t：检查验证码地址改回 `/authserver/checkNeedCaptcha.htl`，不得再请求带 `/common/` 的这个地址。打开滑块仍用 `/authserver/common/openSliderCaptcha.htl`，校验滑块仍用 `/authserver/common/verifySliderCaptcha.htl`。验证：测试断言检查请求路径恰为 `/authserver/checkNeedCaptcha.htl`，另两个请求路径仍含 `/common/`。不改 V1–V18、`sign` 字段、登录字段、课表解析、`kbjcmsid` 和 HTTPS 基址。
- [ ] 任务 3s：两个滑块准备地址都加上 `/common/`。`checkNeedCaptcha.htl` 改为 `/authserver/common/checkNeedCaptcha.htl`，`openSliderCaptcha.htl` 改为 `/authserver/common/openSliderCaptcha.htl`。验证：测试断言两个完整路径都包含 `/common/`。不改 V1–V18、`sign` 字段、登录字段、课表解析、`kbjcmsid` 和 HTTPS 基址。
- [ ] 任务 3r：滑块校验地址改为 `/authserver/common/verifySliderCaptcha.htl`。不得请求不带 `/common/` 的地址。验证：测试断言完整路径包含 `/authserver/common/verifySliderCaptcha.htl`。不改 V1–V18、`sign` 字段、登录字段、课表解析、`kbjcmsid` 和 HTTPS 基址。
- [ ] 任务 3p：滑块校验字段改为 `sign`。`verifySliderCaptcha.htl` 提交 `sign=加密结果`，不得提交 `captcha`。验证：请求体包含 `sign` 且不包含 `captcha`。不改 V1–V18、登录字段、课表解析、`kbjcmsid` 和 HTTPS 基址。
- [ ] 任务 3q：同步页显示滑块。需要滑块时展示背景图和小图，操作者拖动后提交 `sliderCanvasWidth`、`sliderMove`、`sliderTracks`；不需要时保持现有流程。验证：`npm run build` 通过。不改 V1–V18 和后端登录协议。
- [ ] 任务 3o：支持认证页滑块。`checkNeedCaptcha.htl` 返回需要验证时，调 `openSliderCaptcha.htl`，返回背景图和小图；同步时先把操作者提供的滑块位移提交到 `verifySliderCaptcha.htl`，只有 `errorCode=1` 才登录。不取 `getCaptcha.htl`，不提交 `passwordText`。验证：需要滑块时返回两张图片；校验失败不发登录请求；校验成功后登录请求仍不含原始密码。不改 V1–V18、课表解析、`kbjcmsid` 和 HTTPS 基址。
- [ ] 任务 3m：登录字段只取账号登录表单。认证页有多个同名 `lt`、`execution`，必须取包含 `pwdEncryptSalt` 的那个表单，不能取页面最后一次出现的值。验证：样例页前面的值是 `e1s1`、账号表单的值是 `e2s2` 时，提交使用 `e2s2`。不改 V1–V18、密码加密、跳转、Cookie、课表解析、`kbjcmsid` 取值和 HTTPS 基址。
- [ ] 任务 3l：密码随机串改用认证页字符集 `ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678`，不能含 `I、L、O、l、o、0、1、9`。向量仍只参与加密，登录请求只提交单个 Base64 密文。验证：生成的 64 位前缀和 16 位向量都只含该字符集；解密结果为前缀加原密码。不改 V1–V18、跳转、Cookie、课表解析、`kbjcmsid` 取值和 HTTPS 基址。
- [ ] 任务 3k：补上任务 3j 的遗漏。Cookie 按响应头的 Domain 归属；没有 Domain 时才归当前主机。访问 `jw.dgpt.edu.cn` 时必须带上 `.dgpt.edu.cn` 的 Cookie。验证：认证站下发 `Set-Cookie: CASTGC=value; Domain=.dgpt.edu.cn` 后，后续教务请求仍带该 Cookie。不改 V1–V18、课表解析、`kbjcmsid` 取值和 HTTPS 基址。
- [ ] 任务 3j：补上任务 3i 的遗漏。统一认证跳回带 `ticket` 的 `sso.jsp` 时，不能停止，必须继续访问该地址并收集后续 Cookie，直到教务页面不再跳转。验证：登录 302 到带 ticket 的 sso.jsp，该页再 302 并下发教务 Cookie，后续总表请求仍带该 Cookie。不改 V1–V18、课表解析、`kbjcmsid` 取值和 HTTPS 基址。
- [ ] 任务 3i：补上任务 3h 的遗漏。门户登录返回 302 后，必须从该 Location 开始逐跳跟随并收集 Cookie，不能只读取登录这一次响应。验证：登录响应 302 到下一地址，下一地址再下发 Cookie，后续教务请求仍带该 Cookie。不改 V1–V18、课表解析、`kbjcmsid` 取值和 HTTPS 基址。
- [ ] 任务 3h：门户登录和教务换票都手工跟随跳转，并收集每一跳 Cookie。`sso.jsp` 的页面脚本跳转也要跟随，不能只认 HTTP 302。认证站与教务站的同名 Cookie 按域名分别保存，访问哪个域名就只带该域名的 Cookie。总表页没有 `#kbjcmsid` 仍返回登录失败。验证：门户登录中途 Cookie 不丢；脚本跳转会继续访问；两个域名的同名 Cookie 不互相覆盖。不改 V1–V18、课表解析、`kbjcmsid` 取值和 HTTPS 基址。
- [ ] 任务 3g：教务换票必须收集跳转链上每一跳的 Cookie，不能只保留最终响应的 Cookie。随后读取总表页，没有 `#kbjcmsid` 时才返回登录失败。删除用 `xsMain` 判断 `sso.jsp` 成功的规则。验证：中途响应发出的教务 Cookie 会带到总表页；只存在于最终响应的 Cookie 不再是唯一来源；总表页没有 `#kbjcmsid` 仍失败。不改 V1–V18、课表解析、`kbjcmsid` 取值和 HTTPS 基址。
- [ ] 任务 3f：教务换票不能只看 HTTP 200。`sso.jsp` 跟随跳转后，最终页面标题为「登录」或正文不含教务主页标记时，返回登录失败，不继续读取总表页。验证：200 的登录页失败；含教务主页标记的页面继续。不改 V1–V18、课表解析、`kbjcmsid` 取值和 HTTPS 基址。
- [ ] 任务 3e：教务基址改为 `https://jw.dgpt.edu.cn`。`TimetableProperties` 默认值和 `application.yml` 的 `TIMETABLE_JW_BASE_URL` 默认值都改，不再使用 `http://jw.dgpt.edu.cn`。读取总表页时跟随跳转；最终页面没有 `#kbjcmsid` 时返回明确错误，不能把登录页继续交给解析。验证：默认地址以 `https://jw.dgpt.edu.cn` 开头；没有该下拉框的页面失败。不改 V1–V18、课表解析和 `kbjcmsid` 取值规则。
- [ ] 任务 3b：新增 `V18__create_timetable_import_teacher.sql`，内容与 `.specs/timetable-SCHEMA.sql` 末尾的 V18 表一致。同步时，已映射且出现在总表中的教师都写入该表，整学期无课也要写。验证：Flyway 走到 18；无课教师有导入记录但没有单元格。
- [ ] 任务 3c：把备份可接受的最高 Flyway 版本从 17 改为 18，数字仍只留在 `scripts/verify-backup.py` 的 `DEFAULT_MAX_SCHEMA`。验证：schema 18 被默认接受，schema 19 被拒绝；`restore-backup.sh` 不传 `--max-schema` 时结果相同。
- [ ] 任务 4：实现 `GET /api/timetable/common-free`。专业或教师二选一，按周判断占用。验证：任一教师占用则该格不空闲；无批次时 42 格都是 `free=false`；专业中的未映射教师返回 `NO_TIMETABLE_MAPPING`；`timetable_import_teacher` 没有该教师时返回 `ABSENT_FROM_IMPORT`；有导入记录但没有单元格时该教师参与判断并算空闲。
- [ ] 任务 5：增加同步页、查询页和侧栏入口。教师与超级管理员都可查询，映射仍在组织管理页。验证：`npm run build` 通过。
- [ ] 任务 5b：同步失败时保留已填写的教务密码和验证码，只有成功后清空。超级管理员侧栏增加教师映射入口，仍指向 `/dashboard/admin/organization`。验证：失败路径不清空；成功路径清空；`npm run build` 通过。
- [ ] 任务 6：实现课表 Excel，并给 AI 增加 `timetableOccupancy`。验证：未选模块时载荷不含课表；导出为 xlsx。

任一任务和契约不符时停下来，不要自行改接口或表。
