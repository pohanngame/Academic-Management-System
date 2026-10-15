# AI Agent 交接

## 本次已落地

- 现状基线仍在 `.specs/PRD.md`、`.specs/API.yaml`、`.specs/SCHEMA.sql`。业务代码未改。
- 已核实 DGPT 登录链：门户 POST `authserver.dgpt.edu.cn/authserver/login`；教务再经 `jw.dgpt.edu.cn/jsxsd/sso.jsp` 换票据，会话独立。
- 教师总表为 POST `/jsxsd/kbcx/kbxx_teacher_ifr`。一个学期可一次返回全校 `#kbtable`，约 760 名教师。
- 课表增量已改写：`.specs/timetable-PRD.md`、`.specs/timetable-API.yaml`、`.specs/timetable-SCHEMA.sql`、`TASK.md`。旧的手工粘贴任务已删除。

## 遗留/已知问题

- 本次使用的是学生端账号。全校教师总表能打开，但是否允许系统化抓取尚未得到学校确认。
- 总表没有稳定教师 ID，只能先按显示姓名映射。同名不能自动合并。
- 单元格不能区分调课、占位、实验课。后三个大节的时钟时间本次未看到，不能编造。
- 教务密码、Cookie、Session、票据都不得入库、写日志或进备份。
- `teaching_course` 仍是成果档案。一名教师当前只属于一个专业。

## 下一步待办

任务 1 至 6、以及 3d–3w 已落地（3n 已作废）。
2026-10-02 公共空闲查询的专业/教师下拉已上线：新增登录可访问的 GET `/api/timetable/common-free/options`，页面不再调超管专用的专业接口。
后端 jar 与前端静态文件已换入现运行容器，未重建镜像。数据库仍是 schema 18，数据卷未动。
下一步：强刷 http://localhost:8088 ，打开公共空闲查询，学期用 2026-2027-1，按教师选张挥与梁轰。

### 任务 3w（已落地）

登录不带 service 时只停在个人中心。登录链结束后，若最终地址不在 `/jsxsd/` 下，再访问一次 `jwSsoUrl`。
验证：本机对真实教务站取到的教师课表页含 kbjcmsid（约 32505 字符）；诊断用的临时真实登录测试已删除。
课表相关测试 125 条、0 失败。只重建后端，镜像 sha256:7f63f9f81da92bb083e6cd7b77b2338c4510eb91c1e76036f940c928f728cd08。

### 任务 3v（已落地）

修正 Cookie 归属：host-only 与带 Domain 彻底分开。

根因：`CookieJar` 把**所有**归属都当成域 Cookie 做后缀匹配，
于是认证站不带 Domain 的 `CASTGC` 会被误当作 `*.dgpt.edu.cn` 向外发，
再被教务同名 Cookie 覆盖。

改动：

- `CookieJar` 新增 `hostOnly` 归属集合；`putHostOnly(host, path, name, value)`
  只归当前主机，`cookiesDomainMatches` 对 host-only 键**仅精确匹配**，不外溢到父/子域。
- Cookie 存储键改为 `Path + "\n" + 名`，同名 Cookie 在不同 Path 下不再互相覆盖；
  `headerFor` 新增 RFC 6265 的 `pathMatches` 过滤。
- 父域先放、主机自身后放，后者同名时覆盖前者；**host-only 不会被同名父域 Cookie 覆盖**。
- `collectInto`：无 Domain → `putHostOnly(url.host(), parsed.path(), ...)`；
  有 Domain → `putFrom(domain, ...)`。
- `parseSetCookie` 新增 `path` 字段（`Domain` 后不再 `break`，否则拿不到 `Path`）。
- 带 Domain 的 Cookie 仍按其域及子域发送（原有行为不变）。

测试：`JsxsdTeacherTablePageTest` 22 → 26 条，新增 4 条：

- `hostOnlyCastgcStaysOnTheAuthHostAndNeverLeaksToAcademicAffairs`（你点名的验收）；
- `hostOnlyCookieNeverEscalatesToParentOrChildDomains`（旧代码真正出错处：下发到子域/父域）；
- `parentDomainCookieDoesNotOverwriteTheHostOnlyOne`（同名父域不得覆盖）；
- `hostOnlyCookiesAreKeptSeparatelyByPath`（按 Path 分开）。

原 `hostOnlyCookieIsNotSentToOtherHosts` 改用 `putHostOnly`（它原先用 `putFrom` 存，
本身就没覆盖到真正的 bug 场景）。

**反向验证（均已恢复，共 3 轮）**：

1. 去掉 host-only 短路 → `hostOnlyCookieNeverEscalatesToParentOrChildDomains` 失败；
2. 去掉 Path 过滤 → `hostOnlyCookiesAreKeptSeparatelyByPath` 失败；
3. 颠倒优先级 → 6 条失败（含同名覆盖与跨域发送）。

验证：`JsxsdTeacherTablePageTest` 26 tests / 0 failures；
后端子集 124 tests / 0 failures / BUILD SUCCESS；
全量 352 tests / 0 failures / 54 errors（均为既有 MySQL 连接环境错误，与本次无关）。

本次只动 3 个文件：`JsxsdClient.java`、`TimetableSyncAttemptStore.java`、
`JsxsdTeacherTablePageTest.java`。
登录字段、课表解析、`kbjcmsid`、HTTPS 基址、三个认证页地址、`sign` 字段均未动；
V1–V18、三份课表契约未动；Cookie 值不进日志/响应/数据库（两个 `toString` 只打印计数）。

> **踩坑记录**：本轮曾用 `git checkout --` 回滚反向验证的临时改动，
> 结果连带抹掉了尚未提交的正式修改（该文件从未提交过）。
> 已用备份重新应用并复测通过。**教训：本项目工作区长期未提交，
> 禁止用 git checkout/restore 回滚，一律用文件级备份。**

> **未验证项**：真实教务站仍未跑通。本次修复依浏览器诊断与本地 `HttpServer` 单元测试，
> 真实站的 `Set-Cookie` 究竟有无 `Domain`/`Path` 尚未逐条抓包确认。


### 任务 3t（已落地）

认证页三个地址的最终状态（以实测为准）：

```java
+ "/authserver/checkNeedCaptcha.htl?username="                          // 不带 /common/
+ "/authserver/common/openSliderCaptcha.htl?ts=" + ...                 // 带 /common/
+ "/authserver/common/verifySliderCaptcha.htl";                          // 带 /common/
```

- 3s 把 `checkNeedCaptcha` 也改成 `/common/` 是错的：实测带 `/common/` 返回 **404**，
  不带才 200。本轮改回不带 `/common/`，与契约 `.specs/timetable-API.yaml:41` 一致。
- **三个地址并不统一**，切勿再“顺手对齐”成一个前缀；逐个按实测为准。
- 打开滑块、校验滑块保持带 `/common/` 不变；`sign` 字段、`errorCode=1`、登录字段未动。

测试：`JsxsdSliderCaptchaTest` 的遍历用例更名为 `sliderPreparationUsesTheExpectedPaths`，
断言检查路径**恰为** `/authserver/checkNeedCaptcha.htl` 且**不含** `/common/`，
同时断言打开滑块路径**含** `/authserver/common/`、`VERIFY_PATH` **含** `/authserver/common/`。

**反向验证**：把 `checkNeedCaptcha` 改回 `/common/` → 2 条用例以 `HTTP 404` 失败（已恢复）。

验证：`JsxsdSliderCaptchaTest` 8 tests / 0 failures；
后端子集 120 tests / 0 failures / BUILD SUCCESS。

本次只动 2 个文件：`JsxsdClient.java`、`JsxsdSliderCaptchaTest.java`。
登录字段、课表解析、`kbjcmsid` 取值、HTTPS 基址、跳转、Cookie、字符集、表单作用域、
`application.yml`、V1–V18、三份课表契约均未动；timetable 包仍无任何日志语句。

> **已由用户真实站实测确认**：`checkNeedCaptcha` 不带 `/common/` 为 200、带 `/common/` 为 404。
> 打开/校验滑块两个 `/common/` 地址仍待真实站完整跑通验证。


### 任务 3s（已落地）

两个滑块准备地址都加上 `/common/`，与契约 `.specs/timetable-API.yaml:41` 一致：

```java
+ "/authserver/common/checkNeedCaptcha.htl?username="
+ "/authserver/common/openSliderCaptcha.htl?ts=" + System.currentTimeMillis();
```

至此**三个认证页地址全部带 `/common/` 前缀**（`checkNeedCaptcha`、`openSliderCaptcha`、
`verifySliderCaptcha`）。

- `sign` 字段、加密内容、`errorCode=1`、登录字段均未动。
- 仍不取 `getCaptcha.htl`（主代码里只剩一句说明注释）。

测试：`JsxsdSliderCaptchaTest` 新增 `sliderPreparationHitsTheCommonPathsOnly`，
用 `getRequestURI().getPath()` 断言两个完整路径依次为
`/authserver/common/checkNeedCaptcha.htl`、`/authserver/common/openSliderCaptcha.htl`；
新增私有助手 `recordJson` 同时记录路径并回包，另两条用例的桩地址收敛为常量
`CHECK_PATH` / `OPEN_SLIDER_PATH`。

**反向验证**：把两个地址都改回不带 `/common/` → 2 条用例以 `HTTP 404` 失败（已恢复）。

验证：`JsxsdSliderCaptchaTest` 8 tests / 0 failures；
后端子集 120 tests / 0 failures / BUILD SUCCESS。

本次只动 2 个文件：`JsxsdClient.java`、`JsxsdSliderCaptchaTest.java`。
登录字段、课表解析、`kbjcmsid` 取值、HTTPS 基址、跳转、Cookie、字符集、表单作用域、
`application.yml`、V1–V18、三份课表契约均未动；timetable 包仍无任何日志语句。

> **仍待真实站确认**：三个地址现已全部带 `/common/`（依你与契约）。真实教务站仍未连通，
> 所有验证均在本地 `HttpServer` 上完成。


### 任务 3r（已落地）

滑块校验地址修正为 `/authserver/common/verifySliderCaptcha.htl`。

```java
String url = trimTrailingSlash(properties.getAuthBaseUrl())
        + "/authserver/common/verifySliderCaptcha.htl";
```

- 3p 当时只改了字段名，没改路径；真实站要的是带 `/common/` 的地址。
- 契约 `.specs/timetable-API.yaml:64` 已写明该路径，本轮与契约一致。
- `sign` 字段、加密内容（`{canvasLength, moveLength, tracks}`）、`errorCode=1` 规则均未动。
- 另外两个认证页地址（`checkNeedCaptcha.htl`、`openSliderCaptcha.htl`）**未带 `/common/`，保持不变**。

测试：`JsxsdSliderCaptchaTest` 新增 `verificationHitsTheCommonVerifyPathOnly`，
用 `exchange.getRequestURI().getPath()` 断言完整路径恰为
`/authserver/common/verifySliderCaptcha.htl`；其余 3 条用例的桩地址同步改为常量
`VERIFY_PATH`，避免路径写多份。

**反向验证**：把地址改回不带 `/common/` → 4 条用例全部 404 失败（已恢复）。

验证：`JsxsdSliderCaptchaTest` 7 tests / 0 failures；
后端子集 119 tests / 0 failures / BUILD SUCCESS。

本次只动 2 个文件：`JsxsdClient.java`、`JsxsdSliderCaptchaTest.java`。
登录字段、课表解析、`kbjcmsid` 取值、HTTPS 基址、跳转、Cookie、字符集、表单作用域、
`application.yml`、V1–V18、三份课表契约均未动；timetable 包仍无任何日志语句。

> **待真实站确认**：`checkNeedCaptcha.htl` 与 `openSliderCaptcha.htl` 目前仍未带 `/common/`。
> 若真实站这两个也在 `/common/` 下，需下一轮补改（本轮未列入任务范围）。


### 任务 3p（已落地）

滑块校验字段名修正：`verifySliderCaptcha.htl` 提交 `sign`，不再提交 `captcha`。

```java
RequestBody form = new FormBody.Builder()
        .add("sign", encrypted)
        .build();
```

- 3o 误用了 `captcha`，认证页要的是 `sign`；契约 `.specs/timetable-API.yaml:64`
  已写明“以 sign 字段提交”。
- **登录表单的 `captcha` 字段保持不变**（那是门户登录，不是滑块校验）。

测试：`JsxsdSliderCaptchaTest.verificationBodyCarriesSignAndNeverCaptcha` 断言
请求体以 `sign=` 开头、含 `sign=`、**不含 `captcha`**，且仍无明文。

**反向验证**：把字段改回 `captcha` → 恰好只有该测试失败（已恢复）。

### 任务 3q（已落地）

同步页支持滑块：

- `timetableUiRules.js` 新增 `sliderRequired`、`sliderBackgroundSource`、`sliderPieceSource`、
  `buildSliderTracks`、`sliderPayload`、`sliderReady`；删除已死的 `captchaImageSource`。
- `TimetableSyncView.vue` 在 `sliderRequired=true` 时显示背景图 + 可拖动小图，
  用 pointer 事件记录轨迹；`sliderMove` 取**最后一次采样的 x**。
- `services/timetable.js` 的 `runSync` 仅在三个滑块字段**都非空**时才加入请求体，
  不需要滑块时完全不变。
- `buildSliderTracks` 把 `Math.round(-0.4)` 产生的 `-0` 归一成 `0`。

测试：`timetableUiRules.test.js` 51 → 55 条（新增 4 条：两图来源、
轨迹取整、需要滑块的荷载、不需要滑块时不发任何额外字段）。

**反向验证**（均已恢复）：
1. 让 `sliderPayload` 忽略 `sliderRequired` → 只有“不需要滑块时不发额外字段”失败。
2. 让 `runSync` 无条件写入三个滑块键 → 确认服务层门禁位置。

验证：`npm run build` ✓ built；`npm test` 55 pass / 0 fail；
后端子集 118 tests / 0 fail / BUILD SUCCESS。

本次只动 6 个文件：后端 2 个（`JsxsdClient.java`、`JsxsdSliderCaptchaTest.java`），
前端 4 个（`TimetableSyncView.vue`、`timetableUiRules.js`、`services/timetable.js`、
`timetableUiRules.test.js`）。
跳转、Cookie、登录字段、课表解析、`kbjcmsid` 取值、HTTPS 基址、字符集、表单作用域、
`application.yml`、V1–V18、三份课表契约均未动；timetable 包仍无任何日志语句。

> **待确认（真实站未验证）**：`verifySliderCaptcha.htl` 的路径带 `/common/` 前缀
> （用户描述为 `/authserver/common/verifySliderCaptcha.htl`），而代码与契约里是
> `/authserver/verifySliderCaptcha.htl`。本轮**未改路径**（不在任务范围内）；
> 若真实站确实在 `/common/` 下，需下一轮补改。

### 任务 3o（已落地）

认证页改为滑块验证：

- `checkNeedCaptcha.htl` 需要验证时改调 `openSliderCaptcha.htl`，
  返回 `bigImage`/`smallImage`；**已删除 `getCaptcha.htl` 调用**。
- `CaptchaResponse` 新增 `sliderRequired`、`sliderBackgroundBase64`、`sliderPieceBase64`；
  旧 `captchaImageBase64` 保留字段但固定为 null（不动前端契约）。
- `safeSecure` = 小图 Base64 解码后的**最后 16 字节**（不足 16 字节或非法 Base64 报 502，
  不静默降级），只存在本次尝试的内存（`Attempt.safeSecure`），`toString` 不输出。
- `SyncRequest` 新增 `sliderCanvasWidth`、`sliderMove`、`sliderTracks`；
  `tracks` 用 `List<Map<String,Object>>` 原样承接，避免固定字段丢页面新增字段。
- 同步时先调 `verifySliderCaptcha.htl`，提交体是
  `encryptPassword(JSON.stringify({canvasLength, moveLength, tracks}), safeSecure)` 的单密文；
  **只有 `errorCode=1` 才继续**，否则 400 且**绝不下发登录请求**。
- 登录保持原字段，不提交 `passwordText`、不提交原始密码（原始密码从不进入本链路）。

测试：新增 `JsxsdSliderCaptchaTest` 6 条（本机 `HttpServer` + Mockito 服务层）：

- 需要滑块时返回两张图，且 `safeSecure` 等于小图末 16 字节；
- **反向断言 `getCaptcha.htl` 未被访问**；
- `errorCode=1` 通过、`errorCode=0` 失败；
- 校验请求体只有密文，不含 `canvasLength`/`moveLength`/`tracks` 明文；
- 校验失败时 `verify(login, never())`；
- 校验成功后登录，断言传给 `login` 的是密文且不含原始密码。

同时修修：`TimetableSyncServiceTest` 里两条带滑块桩的用例补上
`verifySliderCaptcha→true`，让它们仍能测原本的登录行为；
`startAttemptReturnsAttemptIdAndCaptchaFlag` 改为断言两图字段、旧字段为 null；
`JsxsdClient` 构造函数新增 `ObjectMapper`（三个旧测试同步更新），
删除已死常量 `MAX_CAPTCHA_BYTES` 与已作废的 3n 测试文件 `JsxsdLoginFormTest`
（它断言已撤回的 `passwordText`，当时已红）。

**三轮反向验证（均已恢复）**：

1. 删掉 sync 的滑块门禁 → 只有 `failedSliderVerificationNeverSendsTheLoginRequest` 失败。
2. 让 `openSliderCaptcha` 打 `getCaptcha.htl` → 只有两图用例失败。
3. 把校验改成永远返回 true → 只有 `errorCode=0` 那条失败。

本次只动 timetable 包内 10 个文件（5 主 + 5 测）。
跳转、Cookie、课表解析、`kbjcmsid` 取值、HTTPS 基址、字符集、表单作用域、
`application.yml`、V1–V18、三份课表契约均未动；timetable 包仍无任何日志语句。

> **遗留提醒（前端未改）**：`frontend/src/views/timetable/timetableUiRules.js` 的
> `captchaImageSource` 仍在读旧的 `captchaImageBase64`（现恒为 null），
> 因此同步页再也显示不出验证图。不报错、构建与 51 条前端测试均绿，
> 但**滑块图片实际上不可见**，需下一轮给前端补两图 + 轨迹上报。

### 任务 3n（已落地）

登录提交与认证页对齐：`password` 只放加密结果，同时补一个值为空的 `passwordText`。

```java
FormBody.Builder form = new FormBody.Builder()
        .add("username", username)
        .add("password", encryptedPassword)
        .add("passwordText", "")
```

- `login(...)` 入参本就是 `encryptedPassword`，原始密码从不进入本方法，
  因此请求体天然不含明文；`passwordText` 恒为空串，只补齐字段、不承载任何值。
- 密码加密算法、`lt`/`execution` 取值、跳转链、Cookie、`captcha` 提交均未改。

测试：新增 `JsxsdLoginFormTest` 2 条（用本机 `HttpServer` 拓下真实请求体，
按 `application/x-www-form-urlencoded` 解码后断言）：

- `loginSubmitsEncryptedPasswordPlusEmptyPasswordText`：
  断言 `password` 与 `passwordText` 都存在、`passwordText` 为空、
  `password` 等于加密结果，且**原始密码不出现在请求体中**。
- `passwordTextIsAlwaysPresentAndEmpty`：断言恰好一个 `passwordText` 且为空，
  并确认 `captcha` 仍照常提交（不被该字段挤掉）。

**两轮反向验证（均已恢复）**：

1. 删掉 `.add("passwordText", "")` → 恰好两条都失败。
2. 把 `passwordText` 改成携带非空值 → 恰好两条都失败。

本次只动：`JsxsdClient.java`、`JsxsdLoginFormTest.java`。
密码加密算法、跳转、Cookie、课表解析、`kbjcmsid` 取值、HTTPS 基址、`application.yml`、
V1–V18、三份课表契约均未动；timetable 包仍无任何日志语句。

### 任务 3m（已落地）

认证页有多个登录表单且字段同名，`lt`/`execution` 只能取自包含 `id="pwdEncryptSalt"` 的账号登录表单。

- 旧 `parseHiddenInputs` 在**整页**上用 `INPUT_PATTERN` 扫描并 `putIfAbsent`，
  于是拿到的是**页面上第一个**同名 `lt`/`execution`（不一定是账号表单），
  与 `salt` 不配对，教务会拒绝登录。
- 新增 `FORM_PATTERN`（`<form\b[^>]*>(.*?)</form>`，DOTALL）与
  `findAccountLoginForm(html)`：逐表单查找含 `pwdEncryptSalt` 的那个；
  `parseHiddenInputs` 只在该表单内取 `lt`/`execution`。
- `extractSalt` 同步改为在该表单内取 salt（两个方法共用同一个“账号表单”定义，
  避免两份不一致的判定）。
- 找不到账号表单时 `parseHiddenInputs` 返回空 map、`extractSalt` 返回 null，
  `openAuthPage` 仍报原错 `did not provide pwdEncryptSalt`。

提交字段（`username`/`password`/`lt`/`execution`/`_eventId`/`cllt`/`dllt`）与密码加密均未改。

测试：新增 `JsxsdAuthPageTest` 4 条（该文件此前不存在，`openAuthPage` 一直只被 mock）：

- `ltAndExecutionComeFromTheFormThatCarriesPwdEncryptSalt`：
  样例页前面表单是 `e1s1`、账号表单是 `e2s2`，断言实际取到 `e2s2`。
- `valuesAreTakenRegardlessOfFormOrder`：账号表单在后时仍取对的值。
- `missingFieldsInTheAccountFormDoNotFallBackToOtherForms`：
  账号表单里没有这两个字段时取空串，**不退回其他表单**。
- `missingPwdEncryptSaltStillFails`：整页无账号表单时仍报原错。

**反向验证**：把 `parseHiddenInputs` 改回整页扫描后重跑，
恰好只有 `ltAndExecutionComeFromTheFormThatCarriesPwdEncryptSalt` 与
`missingFieldsInTheAccountFormDoNotFallBackToOtherForms` 失败（其余 2 条全过），
证明确实钉住了新行为（已恢复）。

本次只动：`JsxsdClient.java`、`JsxsdAuthPageTest.java`。
密码加密、跳转、Cookie、课表解析、`kbjcmsid` 取值、HTTPS 基址、`application.yml`、
V1–V18、三份课表契约均未动；timetable 包仍无任何日志语句。

### 任务 3l（已落地）

`JsxsdPasswordCipher` 的随机串字符集改为认证页字符集：

```java
private static final String ALPHABET =
        "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678";
```

- 旧值 `A-Za-z0-9`（62 字符）**包含** `I、L、O、l、o、0、1、9`，
  会生成教务端可能不接受的随机串；新值 48 字符，不含上述九个字符。
- 新字符集是旧字符集的**真子集**（只删不加），64 位前缀与 16 位向量都用它。
- AES/CBC/PKCS5Padding（= PKCS7）、`pwdEncryptSalt` 作密钥、
  只提交单个 Base64 密文均**未改**；`PREFIX_LENGTH=64`、`IV_LENGTH=16` 未改。

测试：`JsxsdPasswordCipherTest` 3 → 5 条：

- `prefixAndIvOnlyUseTheAcademicAffairsAlphabet`：跑 200 轮，
  逐字符断言前缀与向量都在字符集内，且九个禁用字符均不出现。
- `decryptsToPrefixPlusPlainPasswordWithFixedRandomness`：
  用只返回固定值的 `SecureRandom` 把随机源变确定（`nextInt` 恒为 7 → `ALLOWED[7]='H'`），
  断言向量为 `"H"*16`、解密结果为 `"H"*64 + "Passw0rd!"`。

**两轮反向验证（均已恢复）**：

1. 字符集改回旧的 62 字符 → 恰好只有 `prefixAndIvOnlyUseTheAcademicAffairsAlphabet` 失败
  （`prefix char q must be in the alphabet`，`q` 已不属于新字符集）。
2. 保持 48 字符但**换顺序**（把 `H` 提到下标 0）→ 恰好只有
  `decryptsToPrefixPlusPlainPasswordWithFixedRandomness` 失败，说明固定随机源那条
  确实钉住了字符集本身，而不只是“长度 64”。

本次只动：`JsxsdPasswordCipher.java`、`JsxsdPasswordCipherTest.java`。
跳转、Cookie、课表解析、`kbjcmsid` 取值、HTTPS 基址、`application.yml`、V1–V18、
三份课表契约均未动；timetable 包仍无任何日志语句，密码与前缀不入库、不进响应。

### 任务 3k（已落地）

补 3j 的遗漏：**域级 Cookie 被错误地记在当前主机名下**。

- 认证站下发 `Set-Cookie: CASTGC=...; Domain=.dgpt.edu.cn`，
  旧代码 `collectInto` 总是 `jar.putAllFrom(url.host(), ...)`，
  CASTGC 被记在 `authserver.dgpt.edu.cn` 名下，访问 `jw.dgpt.edu.cn` 时带不上，
  教务始终是未登录状态。

改动（均在 timetable 包内）：

1. `JsxsdClient.collectInto` 改为逐条解析：新增 `parseSetCookie(String)` 与 `ParsedCookie(name, value, domain)`，
   有 `Domain` 就归该域名（去掉前导/尾部点并转小写），没有才归 `url.host()`。
2. `TimetableSyncAttemptStore.CookieJar.headerFor` 改为**按域名树匹配**：
   除主机自身外，还要带上能覆盖它的父域 Cookie（`dgpt.edu.cn` → `jw.dgpt.edu.cn`）；
   父域先放、主机后放，同名时主机覆盖。同时添加 `putFrom`、
   删除了已无调用者的 `collectCookies`（死代码）。
3. 新增 `CookieJar.domainsHolding(name)`（包级，仅返回域名集合、**不返回值**）供测试断言归属。

域名匹配边界：`xdgpt.edu.cn`、`other.edu.cn` **不得**拿到 `dgpt.edu.cn` 的 Cookie
（要求前置字符是 `.`，仅同后缀不算子域名）。

测试：`JsxsdTeacherTablePageTest` 17 → 22 条：

- `castgcFromAuthServerIsSentToTheAcademicAffairsHost`：CASTGC 在教务域必须带上，
  且 `other.edu.cn` / `xdgpt.edu.cn` 不得带上。
- `hostOnlyCookieIsNotSentToOtherHosts`：无 Domain 的 JSESSIONID 不发到教务域。
- `setCookieDomainAttributeDecidesTheOwningDomain`：解析层断言（带 Domain / 无 Domain / 大小写）。
- `loginFilesDomainScopedAndHostOnlyCookiesCorrectly`：**走真实 login 流程**，
  带 Domain 的归 `dgpt.edu.cn`、无 Domain 的归当前主机。
- `domainScopedCookieFlowStillFailsWhenTheTablePageHasNoKbjcmsid`：
  无 `#kbjcmsid` 仍报 `rejected the credentials or captcha`。

**两轮反向验证（均已恢复）**：

1. `headerFor` 改回精确主机匹配 → 恰好只有 `castgcFromAuthServerIsSentToTheAcademicAffairsHost` 失败（其余 20 条全过）。
2. `collectInto` 忽略 `Domain` 属性 → **一开始 21 条全过，没被测出来**；
   因为第一个用例用手工 `putFrom` 构造 jar，根本没走解析/归属逻辑。
   补上 `loginFilesDomainScopedAndHostOnlyCookiesCorrectly`（真实流程）后再验，
   才恰好只有它失败。**这是一个真实的测试覆盖缺口，已补。**

本次只动：`JsxsdClient.java`、`TimetableSyncAttemptStore.java`、`JsxsdTeacherTablePageTest.java`。
课表解析、`kbjcmsid` 取值、HTTPS 基址、`application.yml`、V1–V18、三份课表契约均未动；
timetable 包仍无任何日志语句，`CookieJar.toString` 只输域名数，Cookie 不入库、不进响应。

### 任务 3j（已落地）

补 3i 的遗漏。**真因不是“把 Location 当起点”，而是 3g 引入的自环守卫过于激进**：

- 统一认证成功后 302 回 `/jsxsd/sso.jsp?ticket=...`；该页（待换票形态）
  会先返回 200 + 指向**自己**的脚本跳转，再访一次才真正 302 到教务首页并下发 `JSESSIONID`。
- 旧守卫 `if (next.equals(url)) return;` 把“指向自己”当成自环**立即停下**，
  只访了一次 sso.jsp，导致教务 `JSESSIONID` 从未收到，
  总表页带着空 Cookie 出去。

改为只在**连续两次**拿到同一地址时才停下，允许重复一次：

```java
String previousUrl = null;
...
// 连续两次拿到同一地址才是真自环；只重复一次时不拦，否则会错过后续跳转。
if (next.equals(url) && next.equals(previousUrl)) {
    return;
}
previousUrl = url;
url = next;
```

无限循环仍由既有的 `MAX_REDIRECTS = 10` 兜底，**未另写第二套跳转逻辑**，
仍然只用 `followChainWithCookies`。脚本跳转、按域名保存 Cookie、
总表页 `#kbjcmsid` 成功标准均未变。

测试：`JsxsdTeacherTablePageTest` 15 → 17 条：

- `ticketSsoPageThatRedirectsToItselfIsFollowedUntilItStops`：
  登录 302 → `sso.jsp?ticket=abc`，第 1 次返回指向自己的脚本跳转，
  第 2 次 302 到教务首页并下发 `JSESSIONID`；断言 sso 被访 **2 次**、
  Cookie 已收到、总表页请求带上它。
- `ticketFlowStillFailsWhenTheTablePageHasNoKbjcmsid`：同一条链上
  总表页没有 `#kbjcmsid` 仍报 `rejected the credentials or captcha`。

**踩坑**：前两次用探针（临时 `Probe3j`，已删）模拟“普通 302 链”和
“ticket 页直接 302”，**都能跑通**，说明我一开始猜错了方向；
直到模拟“ticket 页 200 + 指向自己的脚本”才复现（`domains=0`、`TABLE cookie=` 为空），
确认问题在自环守卫而非 3i 的起点赋值。

**反向验证**：把守卫改回 `next.equals(url)` 后重跑，
恰好只有 `ticketSsoPageThatRedirectsToItselfIsFollowedUntilItStops` 失败（其余 16 条全过），
证明测试确实钉住了新行为（已恢复）。

本次只动：`JsxsdClient.java`、`JsxsdTeacherTablePageTest.java`。
课表解析、`kbjcmsid` 取值、HTTPS 基址、`application.yml`、V1–V18、三份课表契约均未动；
timetable 包仍无任何日志语句，Cookie 不入库、不进响应。

### 任务 3i（已落地）

补 3h 的遗漏：`login()` 虽然已关闭自动跳转，但只处理了登录这一次响应，
拿到 `Location` 仅用于查 `error`，**根本没从它继续走**。

- 新增局部变量 `chainStart`，默认仍是 `properties.jwSsoUrl()`；
  当登录响应是 301/302/303/307/308 且带 `Location` 时，
  用 `resolveLocation(authPage.loginUrl(), location.trim())` 把它作为跟随起点。
- **没有另写第二套跳转逻辑**，仍然只调 `followChainWithCookies(client, chainStart, cookies)`。
- 非成功响应且非跳转仍报 `BAD_GATEWAY`（否则带 Location 的 3xx 会被误判为失败）。
- 3h 的脚本跳转、按域名保存 Cookie、总表页 `#kbjcmsid` 成功标准均未变。

测试：`JsxsdTeacherTablePageTest` 13 → 15 条：

- `loginRedirectIsFollowedSoItsSetCookieReachesTheTeacherTablePage`：
  登录响应 302 到一个**旧逻辑永远不会访问**的中间地址（`credential-exchange`），
  该地址才下发 `LOGIN_HOP`；断言中间地址确实被访问、Cookie 收到，
  且随后总表页请求带着它。
- `loginRedirectChainContinuesHopByHop`：登录 302 → step1 → step2 → sso，
  断言途中的 `HOP_A` / `HOP_B` 都被收到。

**两轮踩坑（均仅测试代码问题，不是产品缺陷）**：

1. 第一版把中间地址仍写成 `/jsxsd/sso.jsp`，旧逻辑本来就会访问它，
   结果**反向验证时不报错**，测试根本没钉住新行为。改为指向専用中间地址后才真失败。
2. 中间地址返回 200（无脚本跳转）时链已正常结束，我曾错误断言它会继续跑到 sso。

**反向验证**：把“从登录 Location 继续”那段去掉后重跑，
恰好只有上述两条新用例失败（其余 13 条全过），证明确实钉住了新行为（已恢复）。

本次只动：`JsxsdClient.java`、`JsxsdTeacherTablePageTest.java`。
课表解析、`kbjcmsid` 取值、HTTPS 基址、`application.yml`、V1–V18、三份课表契约均未动；
timetable 包仍无任何日志语句，Cookie 不入库、不进响应。

### 任务 3h（已落地）

一次修正三处登录问题：

1. **门户登录也手工跟跳**：`login()` 用 `followRedirects(false)` 的 client，
   把登录响应也交给同一个 `followChainWithCookies(...)`，中间跳的 Set-Cookie 不再丢。
2. **脚本跳转**：`sso.jsp` 未登录时返回 200 + `window.location.href='...'`（不是 302）。
   新增 `SCRIPT_REDIRECT_PATTERN` 与 `extractScriptRedirect`，在 200 分支里把它当跳转继续访问；
   301/302/303/307/308 仍照旧跟随。加自环保护（地址不变则停）。
3. **Cookie 按域名隔离**：删除 `Map<String,String>` + `cookieHeader(...)`，
   新增 `TimetableSyncAttemptStore.CookieJar`（`byDomain: Map<host, Map<name,value>>`），
   `headerFor(url)` 只带该域名的 Cookie。两个站都有 `JSESSIONID` 再也不会互相覆盖。
   `JsxsdAuthPage.cookies()` 类型改为 `CookieJar`，`fetchTeacherTablePage` / `fetchTeacherTable`
   也改收 `CookieJar`（`TimetableSyncService` 只改了一行变量类型）。

成功标准不变：拿 Cookie 读总表页，含 `#kbjcmsid` 才算成功；没有则 `BAD_REQUEST` +
`Academic affairs rejected the credentials or captcha`。已删除 `xsMain` 判定（已于 3g 完成）。

测试：`JsxsdTeacherTablePageTest` 13 条（原 9 条），新增/改写：

- `cookieFromAnIntermediatePortalHopSurvivesTheFinalResponse`：门户 200 发 `PORTAL_HOP`，
  断言 sso.jsp 请求确实带上了它。
- `scriptRedirectOnSsoPageIsFollowed`：sso 返回 200 + 脚本，断言真的去访问了脚本里的地址。
- `sameCookieNameOnTwoHostsIsKeptSeparately`：两个域名各一个 `JSESSIONID`，互不污染。
- `twoHostsKeepTheirOwnJsessionidThroughTheRealFlow`：真跑一遍同名覆盖行为。

**反向验证**（两次，均已恢复）：把 CookieJar 换回“扁平合并”后，
只有域名隔离用例失败；把脚本跳转分支去掉后，只有脚本跳转用例失败。

**测试踩坑**：本机 `HttpServer` 下教务与认证都是 `127.0.0.1`，**同一个域名**，
无法用 HTTP 验证跨域隔离，所以域名隔离用纯单元测试锁住，
HTTP 用例只能验证同域内的覆盖顺序。

本次只动：`JsxsdClient.java`、`TimetableSyncAttemptStore.java`、`TimetableSyncService.java`（仅类型）、
`JsxsdTeacherTablePageTest.java`、`TimetableSyncServiceTest.java`。
课表解析、`kbjcmsid` 规则、HTTPS 基址、`application.yml`、V1–V18、三份课表契约均未动；
timetable 包仍无日志语句，`CookieJar.toString` 只输域名数，Cookie 值不入库、不进响应。

### 任务 3g（已落地）

教务换票改为**逐跳收集 Cookie**，并删除了 `xsMain` 那套旧规则：

- `login()` 不再用 `isAcademicAffairsHomePage` / `xsMain` / `<title>登录` 判断 sso 是否成功，二者已删除。
- `newClient()` 开着 `followRedirects(true)`，OkHttp 会自动跟跳并**丢掉中间响应**，
  中途下发的教务 Cookie 就永远拿不到。因此新增 `followSsoChainWithCookies(...)`：
  用 `client.newBuilder().followRedirects(false)` 手工跟跳，每跳都 `collectCookies(response)`，
  上限 `MAX_REDIRECTS = 10`，`Location` 用 `HttpUrl.resolve` 补相对路径。
- 登录成败改由**总表页**决定：没有 `#kbjcmsid` 时抛 `BAD_REQUEST`：
  `Academic affairs rejected the credentials or captcha`（原为 `BAD_GATEWAY` + “did not provide kbjcmsid”）。

测试：`JsxsdTeacherTablePageTest` 9 条。新增/改写：

- `cookieFromAnIntermediateHopIsCarriedToTheTeacherTablePage`：sso 先 302 下发 `JW_HOP`，
  最终 200 不再重发；断言它进了 Cookie 且**总表页请求确实带上了它**。
- `tablePageWithKbjcmsidContinues`（有下拉框则继续）、`httpOkLoginPageIsNotTreatedAsASuccessfulTicketExchange`、
  `homeMarkerNoLongerDecidesSuccess`（含 xsMain 也不再算成功）。
- 原有两条总表页用例改为断言 `rejected the credentials or captcha`。

**反向验证**：把手工跟跳换回自动跟跳后重跑，恰好只有 Cookie 链用例失败（中间跳的 Cookie 丢失），
证明该测试真的钉住了新行为（已恢复）。

本次只动：`JsxsdClient.java`、`JsxsdTeacherTablePageTest.java`。
课表解析、`kbjcmsid` 取值规则、HTTPS 基址、`application.yml`、V1–V18、三份课表契约均未动；
timetable 包仍无任何日志语句，Cookie 不入库、不进响应。

### 任务 3f（已落地）

教务换票不再只看 HTTP 200。`JsxsdClient.login` 在 `sso.jsp` 跟随跳转后读取最终页面：

- 标题含「登录」→ 登录失败。
- 正文不含教务主页标记（PRD 指定的 `/jsxsd/framework/xsMain.jsp`，取 `xsMain` 作为标记）→ 登录失败。
- 两种情况都抛 `BAD_REQUEST`：`Academic affairs rejected the credentials or captcha`。
- 含主页标记的页面保持现有流程；失败时 `login(...)` 直接抛出，
  因此 `TimetableSyncService` 第 92 行的 `fetchTeacherTablePage` / `fetchTeacherTable` 根本不会被执行。

测试：`JsxsdTeacherTablePageTest` 增至 8 条（新增 HTTP 200 登录页失败、无标记页失败、有标记页继续）——
用本机 `HttpServer` 真跑 POST 登录 + GET sso 两次请求；
`TimetableSyncServiceTest` 增至 11 条（新增 `rejectedTicketExchangeStopsBeforeReadingTheTeacherTable`，
用 `verify(never())` 钉住失败后不读总表）。

**反向验证**：临时移除该检查后，恰好只有两条失败用例报错，“有标记页继续”仍通过（已恢复）。

**测试踩坑**：`HttpServer` 不读掉 POST 请求体会直接拒绝，`respond(...)` 已加 `readAllBytes()`。

本次只动：`JsxsdClient.java`、`JsxsdTeacherTablePageTest.java`、`TimetableSyncServiceTest.java`。
课表解析、`kbjcmsid` 取值规则、HTTPS 基址、V1–V18、三份课表契约均未动。

### 任务 3e（已落地）

教务基址改为 **https**（`: 01` 不再出现在任何地方）：

- `TimetableProperties.jwBaseUrl` 默认值 → `https://jw.dgpt.edu.cn`。
- `application.yml` 的 `TIMETABLE_JW_BASE_URL` 默认值 → 同上。
- `sso.jsp`、总表页 `kbxx_teacher`、总表数据 `kbxx_teacher_ifr` 三个地址都从这一个基址拼出，全部走 https。
- 总表页**跟随跳转**（`newClient()` 本来就开了 `followRedirects(true)`，四处调用点共用）。
- **新增守卫**：`fetchTeacherTablePage` 拿到最终响应后先查 `#kbjcmsid`，没有就报 `BAD_GATEWAY`，
  避免会话失效时把登录页交给 `extractKbjcmsid`。守卫只管“有没有下拉框”，取值规则仍归 `extractKbjcmsid`。

测试：`JsxsdTeacherTablePageTest`（新，5 条）用本机 `HttpServer` 真实跑 HTTP：跟随跳转到真总表页、
跳转到登录页失败、直接返回登录页失败、有下拉框但仍走取值规则、三个地址都是 https 前缀；
`JsxsdKbjcmsidTest` 增至 13 条（新增默认基址 https、无 `#kbjcmsid` 页面失败）。

**反向验证**：临时移除守卫后，恰好只有两条登录页用例失败，说明测试确实钉住了该行为（已恢复）。

本次只动：`JsxsdClient.java`、`TimetableProperties.java`、`application.yml`、
`JsxsdKbjcmsidTest.java`、`JsxsdTeacherTablePageTest.java`。
课表解析、登录加密、`kbjcmsid` 取值规则、V1–V18、三份课表契约均未动。

### 任务 3d（已落地）

`JsxsdClient.extractKbjcmsid` 改为**单遍扫描**，取值优先级：

1. 带 `selected` 的选项 → 用它（`selected` 与 `value` 先后顺序不限）。
2. 无选中项时，**只有当整个下拉框里恰好只有一个带 value 的选项**才用它。
3. 多个选项都没有 `selected` → 仍报 `BAD_GATEWAY`，**不取第一个、不回退成 0102**。
4. 找不到 `#kbjcmsid`、选项没有 value、页面为空 → 仍报错。

`JsxsdKbjcmsidTest` 从 7 条增至 11 条，新增：唯一选项成功、多选项无选中失败、
有 `selected` 时优先于前面的选项、唯一选项但无 value 仍报错。原 `noSelectedOptionFailsInsteadOfTakingTheFirst` 保留。

只动了 `JsxsdClient.java` 与 `JsxsdKbjcmsidTest.java`；登录、密码加密、课表解析、V1–V18、三份课表契约均未动。

### 部署（2026-09-27，已核实）

- 只重建后端与前端镜像并重启，未执行 `down -v`，数据库卷未动。
- Flyway 已校验 18 个迁移，并将 `academic_profile` 从 17 迁移到 `18 - create timetable import teacher`，成功。
- `timetable_import_teacher` 已存在；`user` 与 `teacher_profile` 各 63 行，未清空。
- 五个服务均 healthy。前端包内含 `timetable-sync`、`timetable-common-free`、课表同步。入口仍是 http://localhost:8088。

### 任务 6（已落地）

- 后端新增 `TimetableCommonFreeExcelService`：`GET /api/timetable/common-free/excel`，
  **直接复用 `TimetableCommonFreeService.query(...)`**，筛选与判定与查询接口完全一致；
  产出真 xlsx，三张表（空闲总览 / 占用明细 / 未参与判断），只借用 `ExportFile` 这个 DTO，未复用成果导出服务。
- AI 新增 `timetableOccupancy` 模块（已入 `moduleMap`）：只有本次请求选中时才采集，
  未选中时载荷不含课表，且**不会去调课表服务**（已有测试钉住）。
  `AiGenerationRequest` 新增 `timetableQuery`；缺参数时返回空行，不回退到任何默认范围。
- 查询页新增“导出 Excel”按钮，走带鉴权的 blob 下载（不把 token 拼到 URL）。

### 踩过的坑

- `AiDataCollector.selectedFields` 用 `ALLOWED_FIELDS` 做模块白名单，新增模块必须同步补字段集，
  否则直接报 `Invalid AI material module`。已补 `timetableOccupancy` 的七个字段。

### 任务 3b（已落地）

- 新增 `V18__create_timetable_import_teacher.sql`，与 `.specs/timetable-SCHEMA.sql` 末尾 V18 表逐字一致；未改 V1–V17。
- 新增 `TimetableImportTeacher` + `TimetableImportTeacherMapper`（复合主键 `(batch_id, teacher_id)`）。
- 同步时**已映射且出现在总表中的教师一律写入该表**，整学期无课也写；空单元格仍不入 `timetable_cell`。
- 替换同学期旧批次时，连同旧的导入教师记录一起删除。

### 任务 3c（已落地）

- `scripts/verify-backup.py` 的 `DEFAULT_MAX_SCHEMA` 由 17 改为 18，仍是唯一写死版本号的地方。
- `restore-backup.sh` 不写死版本（从 `verify-backup.py --help` 解析），未改。

### 任务 4（已落地）

`GET /api/timetable/common-free` 按教师分类参与空闲判断，`timetable_import_teacher` 是关键依据：

- 无课表映射 → `NO_TIMETABLE_MAPPING`，不参与判断。
- 已映射但本批次无导入记录 → `ABSENT_FROM_IMPORT`，不参与判断。
- 有导入记录 → 参与判断；整学期无单元格也算空闲。
- 无批次时 `missingImport=true`，42 格全部 `free=false`（已映射教师标 `ABSENT_FROM_IMPORT`）。
- 任一参与教师占用则该格不空闲；`majorId`/`teacherIds` 二选一，不存在的教师仍 404。

对应测试 `mappedButAbsentFromImportIsExcluded` 已启用，另补 `importedTeacherWithoutCellsCountsAsFree`。

### 任务 5b（已落地）

- 同步**仅成功时**清空教务密码与验证码；失败（含验证码错误、教务拒绝）一律保留，便于直接重试。
  判定抽到 `timetableUiRules.js` 的 `shouldClearSecrets` / `secretsAfterSync`，页面实际调用的就是被测试的函数。
- 超级管理员侧栏“系统管理”分组新增“课表教师映射”入口，指向既有 `/dashboard/admin/organization`，**未新建页面**。
  该入口 `index` 与“专业与业务角色”不同（否则两项会同时高亮），跳转靠 `openTeacherMapping`。

### 任务 5（已落地）

前端新增三个文件：`src/services/timetable.js`、`src/views/timetable/TimetableSyncView.vue`、`CommonFreeView.vue`、
加纯逻辑 `timetableUiRules.js` 与测试 `tests/timetableUiRules.test.js`。

- 路由 `/dashboard/timetable-sync`、`/dashboard/timetable-common-free`，侧栏新增“课表”分组，**两种角色都可见**。
- 教师姓名映射仍在组织管理页，未新开映射页。
- 查询页展示 `missingImport` 警告、7x6=42 格结果、`excludedTeachers` 及其 `reason`（两种 reason 分开标注）。
- 同步页两步走：`/timetable/sync/captcha` → 需要时显示验证码 → `/timetable/sync`；口令只存内存，提交后立即清空。
- 页面无 localStorage/sessionStorage/cookie/console 调用，口令不落盘。

**踩过的坑**：`roleNavigation.js` 原先把 `/dashboard` 下非 `admin` 的路径全部视为教师专属，
导致 SUPER_ADMIN 被挡在两页之外。已新增共享名单 `SHARED_DASHBOARD_PATHS`，不影响原有的角色隔离。

**另一个坑**：JS 会把 `'0102'` 这类数字样式字符串键当整数下标提前，
`Object.keys(SLOT_LABELS)` 会把 `'1112'` 排到首位。已改用显式 `SLOT_ORDER` 数组。

### 环境（已解除）

此前 Docker Desktop 停止时无法实跑 Flyway。2026-09-27 已在运行中的库上迁移到 18 并核实表与旧数据仍在。依赖真实库的集成测试仍未重跑，不影响这次迁移。
