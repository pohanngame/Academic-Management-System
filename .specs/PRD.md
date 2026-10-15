# 现状基线 PRD

本文只描述已经上线的系统边界。课表增量见 `timetable-PRD.md`。不改变现有行为。

## 用户与权限

- 系统角色只有 `TEACHER`、`SUPER_ADMIN`。普通注册只创建 `TEACHER`。
- 业务角色独立存在：`FULL_TIME_TEACHER`、`MAJOR_LEADER`、`VICE_DEAN`、`DEAN`、`PARTY_SECRETARY`。专业负责人必须绑定一个专业，其他业务角色不绑专业。
- 教师业务数据和 AI 配置以当前登录用户的 `teacherId` 为边界。跨教师查看、下载、AI 使用走统一授权。
- `SUPER_ADMIN` 只做系统管理、专业与业务角色、审计、备份和 CCF 导入。不能读教师私人资料、密码哈希、AI Key 明文，不能冒充教师。

## 已上线范围

- 注册、登录、个人资料和简介资料块。
- 项目、授课成果、论文、专利、证书。授课成果表是 `teaching_course`，不是周课表。
- 附件信封加密、BibTeX 导入、证书 OCR 确认后建档。
- Word/Excel 导出、PDF 转换。
- 按教师隔离的 OpenAI-compatible AI 配置和材料生成。
- 完整备份 v2。网页只暂存恢复包并生成命令，不执行恢复。
- SUPER_ADMIN 导入 CCF 2026 后本地匹配。JCR 分区和影响因子只接受手工快照。

## 明确不做

- 不接科睿唯安 API，不自动补全 JCR。
- 不自动下载 CCF JSON。
- 不从网页执行备份恢复。
- 不把首位注册用户自动提升为管理员。
