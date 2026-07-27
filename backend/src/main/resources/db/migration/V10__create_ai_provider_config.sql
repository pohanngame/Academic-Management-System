CREATE TABLE `ai_provider_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `teacher_id` BIGINT NOT NULL,
    `provider_name` VARCHAR(128) NOT NULL,
    `base_url` VARCHAR(2048) NOT NULL,
    `model_name` VARCHAR(255) NOT NULL,
    `encrypted_api_key` TEXT NOT NULL,
    `api_key_last_four` VARCHAR(4) NULL,
    `encryption_key_id` VARCHAR(32) NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ai_provider_config_teacher_id` (`teacher_id`),
    CONSTRAINT `fk_ai_provider_config_teacher`
        FOREIGN KEY (`teacher_id`) REFERENCES `teacher_profile` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
