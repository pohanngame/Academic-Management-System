-- 现状基线。真实迁移仍以 backend/src/main/resources/db/migration/V1–V16 为准。
-- 本文件不作为执行脚本，禁止据此改写已上线字段语义。

-- 身份
-- user(id, username, email, password_hash, role, status, ...)
--   role: TEACHER | SUPER_ADMIN
-- teacher_profile(id, user_id, display_name, department, title, ...)

-- 组织，见 V12
-- major(id, code UNIQUE, name UNIQUE, active, ...)
-- teacher_major(teacher_id PK -> teacher_profile.id, major_id -> major.id)
--   一名教师当前至多一个专业。
-- academic_role_assignment(user_id, role_code, major_id)
--   role_code: FULL_TIME_TEACHER | MAJOR_LEADER | VICE_DEAN | DEAN | PARTY_SECRETARY
--   仅 MAJOR_LEADER 的 major_id 非空。

-- 成果，见 V3。teaching_course 是成果档案，不是周课表。
-- teaching_course(id, teacher_id, course_name, semester, class_name, teaching_target, hours, ...)

-- 授权、附件加密、评级、AI 模板分别见 V13、V14、V15、V16。
-- 下一增量迁移必须从 V17 开始，见 timetable-SCHEMA.sql。
-- 备份校验默认可接受的最高 Flyway 版本与当前迁移一致，现为 17。
-- scripts/verify-backup.py 的 DEFAULT_MAX_SCHEMA 是唯一数字来源。
-- 恢复脚本不另行写死版本；文档中的示例命令也不传 --max-schema。
