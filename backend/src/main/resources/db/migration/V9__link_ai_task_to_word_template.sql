ALTER TABLE `ai_generation_task`
    ADD COLUMN `template_file_id` BIGINT NULL AFTER `teacher_id`,
    ADD KEY `idx_ai_generation_task_teacher_template` (`teacher_id`, `template_file_id`),
    ADD CONSTRAINT `fk_ai_generation_task_template_file`
        FOREIGN KEY (`template_file_id`) REFERENCES `file_metadata` (`id`);
