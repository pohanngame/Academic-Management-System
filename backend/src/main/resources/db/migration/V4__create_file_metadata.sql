CREATE TABLE `file_metadata` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `teacher_id` BIGINT NOT NULL,
    `original_name` VARCHAR(255) NOT NULL,
    `stored_name` VARCHAR(255) NOT NULL,
    `storage_path` VARCHAR(1024) NOT NULL,
    `file_ext` VARCHAR(32) NOT NULL,
    `mime_type` VARCHAR(128) NOT NULL,
    `file_size` BIGINT NOT NULL,
    `business_type` VARCHAR(32) NOT NULL,
    `business_id` BIGINT NULL,
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_file_metadata_teacher_business` (`teacher_id`, `business_type`, `business_id`, `deleted`),
    KEY `idx_file_metadata_teacher_deleted` (`teacher_id`, `deleted`),
    CONSTRAINT `fk_file_metadata_teacher` FOREIGN KEY (`teacher_id`) REFERENCES `teacher_profile` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
