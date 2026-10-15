# 课表公共空闲 PRD（增量）

## 目标

已登录本系统的用户，按专业或勾选教师，查询同一学期、同一周次里大家都没有课的时间。课表可查看、导出，并可作为 AI 材料来源。不进入公开主页。

## 数据来源

教务系统是强智 `jsxsd`，和数字化校园不是同一次登录。

1. 门户登录：POST `https://authserver.dgpt.edu.cn/authserver/login`。账号口令在提交前由页面加密，另有 `lt`、`execution`。提交后必须手工跟随跳转，收集每一跳 Cookie。
2. 教务登录：从门户进入 `https://jw.dgpt.edu.cn/jsxsd/sso.jsp`，统一认证再发一次票据，进入 `https://jw.dgpt.edu.cn/jsxsd/framework/xsMain.jsp`。教务会话独立。教务基址必须是 HTTPS，不能从 `http://jw.dgpt.edu.cn` 发起。`sso.jsp` 未登录时返回的是页面脚本跳转，不是 HTTP 302；HTTP 跳转和页面脚本跳转都要跟随。Cookie 按响应头里的 Domain 归属；没有 Domain 时才归当前主机。访问教务时必须带上 `.dgpt.edu.cn` 的 Cookie，不能只带 `jw.dgpt.edu.cn` 名下的 Cookie。HTTP 200 不是登录成功。统一认证跳回带 `ticket` 的 `sso.jsp` 时，不能把这次跳转当作终点，必须继续访问该地址，直到教务页面不再跳转。随后读取总表页；总表页没有 `#kbjcmsid` 才返回登录失败。不能要求 `sso.jsp` 的最终 HTML 含有 `xsMain`。
3. 教师总表页：`GET https://jw.dgpt.edu.cn/jsxsd/kbcx/kbxx_teacher`。跟随跳转后的最终页面必须含有 `#kbjcmsid`；最终页面没有该下拉框时返回错误，不能把登录页当成总表页。
4. 教师总表数据：POST `https://jw.dgpt.edu.cn/jsxsd/kbcx/kbxx_teacher_ifr`。

2026-09-25 用已登录学生端验证：不填教师时，一学期返回全校教师总表，约 760 行。查询参数：

| 参数 | 含义 |
| --- | --- |
| `xnxqh` | 学期，例如 `2026-2027-1` |
| `kbjcmsid` | 时间模式。默认节次模式的值随学期页面返回 |
| `skyx` | 上课院系 |
| `skjsid` / `skjs` | 教师编号 / 教师姓名。自动完成接口为 POST `/jsxsd/kbcx/queryJs` |
| `zc1` / `zc2` | 开始周 / 结束周，范围 1–30 |
| `skxq1` / `skxq2` | 开始星期 / 结束星期 |
| `jc1` / `jc2` | 开始节次 / 结束节次 |

节次上限另由 GET `/jsxsd/kbxx/initJc?xnxq&kbjcmsid` 返回。

返回仍是 HTML 表 `#kbtable`。第一行是星期，第二行是节次组，从第三行起每位教师一行。每天 6 列，顺序固定为 `0102`、`0304`、`0506`、`0708`、`0910`、`1112`，所以一位教师一行共 43 个 `td`：第 1 列是 `<nobr>` 中的显示姓名，其余列由位置决定星期和节次。没有 `kbcontent` 子格，也没有教师姓名 class。非空单元格按 `<br>` 分成课程、班级、周次原文、教室。空单元格只有空白。没有稳定教师编号，也不能区分调课、占位、实验课。

同步请求的 `kbjcmsid` 取教师总表页 `#kbjcmsid` 的选项值，不能把节次组 `0102` 当作该参数。有 `selected` 时用该选项。没有 `selected` 且只有一个带 value 的选项时，用这一个。有多个选项且都没有 `selected` 时返回错误，不取第一个。星期和节次筛选留空，表示整周；只固定 `zc1=1`、`zc2=30`。

学生个人课表上已见到：第一大节 08:20–09:55，第二大节 10:10–11:45，第三大节 14:20–15:55。后三节时间未在本次打开，不能编造。

## 本系统行为

- 操作者在同步页输入教务账号。后端先打开认证页，按账号调 `checkNeedCaptcha.htl`。账号需要验证时，先调 `openSliderCaptcha.htl` 把滑块图片交给页面；操作者完成后，后端再调 `verifySliderCaptcha.htl`。只有校验成功才提交登录。提交时密码使用页面里的 `pwdEncryptSalt` 做 AES/CBC/PKCS7：明文前加 64 位随机串，向量为 16 位随机串。随机串只能取 `ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678`，不能用完整字母表。向量只参与加密，提交字段仍是单个 Base64 密文。`lt` 和 `execution` 只取含 `pwdEncryptSalt` 的账号登录表单，不取页面里其他登录表单的同名字段。提交字段与页面一致：加密结果放在 `password`。页面点击登录前会禁用密码框，因此不提交 `passwordText`，也不提交原始密码。登录成功后只在这一次同步中使用会话，不把密码、Cookie、Session、票据写入数据库、日志、备份或 `.env`。
- 一次同步只拉一个 `xnxqh` 的全校总表。周次筛选在本系统做，不按周反复请求教务。
- 单元格原文里的周次范围决定它占用哪些周。空单元格表示这些周该位置空闲。
- 教师只按教务显示姓名映射到 `teacher_profile.id`。同名拒绝自动合并，由 SUPER_ADMIN 先处理映射。
- 一次全校总表约有 760 名教师。同步只保存已映射教师的非空单元格；未映射教师记入响应的 `unmappedNames`，不让整批失败，也不把他们算作空闲。
- 已映射教师即使整学期没有课，也要写入 `timetable_import_teacher`。没有这行记录表示该教师未出现在本次导入中。
- 查询可选一个专业，或勾选多名已映射教师。公共空闲指所选教师在该学期、该周、该天、该节次组都没有占用。
- 未映射教师不参与交集，响应单列。没有该学期数据时返回缺失，不能当成全员空闲。
- 导出走课表专用 Excel。AI 使用新模块 `timetableOccupancy`，只带本次查询的教师、课程、教室、星期、节次、周次、占用原文。

## 仍不做

- 不定时登录教务，不保存教务登录态。
- 不写入 `teaching_course`，不进公开主页，不走成果授权。
- 不在学生端权限未经学校确认前，把全校抓取写成默认定时任务。
