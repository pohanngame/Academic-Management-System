CREATE TABLE `export_template` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `teacher_id` BIGINT NOT NULL,
    `template_name` VARCHAR(128) NOT NULL,
    `export_type` VARCHAR(32) NOT NULL,
    `selected_modules` JSON NOT NULL,
    `selected_fields` JSON NOT NULL,
    `field_order` JSON NOT NULL,
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_export_template_teacher_deleted` (`teacher_id`, `deleted`),
    CONSTRAINT `fk_export_template_teacher` FOREIGN KEY (`teacher_id`) REFERENCES `teacher_profile` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
