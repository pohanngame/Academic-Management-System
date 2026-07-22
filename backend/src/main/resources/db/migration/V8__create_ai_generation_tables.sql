CREATE TABLE `ai_generation_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `teacher_id` BIGINT NOT NULL,
    `template_requirement` TEXT NOT NULL,
    `selected_modules` JSON NOT NULL,
    `provider` VARCHAR(64) NOT NULL,
    `model_name` VARCHAR(128) NOT NULL,
    `status` VARCHAR(32) NOT NULL,
    `error_message` VARCHAR(1000) NULL,
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_ai_generation_task_teacher_deleted` (`teacher_id`, `deleted`, `created_at`),
    CONSTRAINT `fk_ai_generation_task_teacher` FOREIGN KEY (`teacher_id`) REFERENCES `teacher_profile` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `ai_generation_result` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `task_id` BIGINT NOT NULL,
    `teacher_id` BIGINT NOT NULL,
    `status` VARCHAR(32) NOT NULL,
    `draft_content` MEDIUMTEXT NULL,
    `edited_content` MEDIUMTEXT NULL,
    `confirmed_content` MEDIUMTEXT NULL,
    `confirmed_at` DATETIME NULL,
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_ai_generation_result_task` (`task_id`, `id`),
    KEY `idx_ai_generation_result_teacher_deleted` (`teacher_id`, `deleted`),
    CONSTRAINT `fk_ai_generation_result_task` FOREIGN KEY (`task_id`) REFERENCES `ai_generation_task` (`id`),
    CONSTRAINT `fk_ai_generation_result_teacher` FOREIGN KEY (`teacher_id`) REFERENCES `teacher_profile` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
