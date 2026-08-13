ALTER TABLE `ai_generation_task`
    ADD COLUMN `template_type` VARCHAR(32) NOT NULL DEFAULT 'SYSTEM_GENERAL' AFTER `template_file_id`;

UPDATE `ai_generation_task`
SET `template_type` = 'CUSTOM'
WHERE `template_file_id` IS NOT NULL;

ALTER TABLE `ai_generation_task`
    ADD CONSTRAINT `chk_ai_generation_task_template_type`
        CHECK (`template_type` IN ('SYSTEM_GENERAL', 'TEACHER_ACHIEVEMENT', 'SIMPLE_REPORT', 'CUSTOM'));
