CREATE TABLE `major` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `code` VARCHAR(64) NOT NULL,
    `name` VARCHAR(128) NOT NULL,
    `active` TINYINT(1) NOT NULL DEFAULT 1,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_major_code` (`code`),
    UNIQUE KEY `uk_major_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `teacher_major` (
    `teacher_id` BIGINT NOT NULL,
    `major_id` BIGINT NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`teacher_id`),
    KEY `idx_teacher_major_major` (`major_id`, `teacher_id`),
    CONSTRAINT `fk_teacher_major_teacher`
        FOREIGN KEY (`teacher_id`) REFERENCES `teacher_profile` (`id`),
    CONSTRAINT `fk_teacher_major_major`
        FOREIGN KEY (`major_id`) REFERENCES `major` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `academic_role_assignment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `role_code` VARCHAR(32) NOT NULL,
    `major_id` BIGINT NULL,
    `scope_major_id` BIGINT AS (COALESCE(`major_id`, 0)) STORED,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_academic_role_user_role_major` (`user_id`, `role_code`, `scope_major_id`),
    KEY `idx_academic_role_scope` (`role_code`, `major_id`, `user_id`),
    CONSTRAINT `fk_academic_role_user`
        FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
    CONSTRAINT `fk_academic_role_major`
        FOREIGN KEY (`major_id`) REFERENCES `major` (`id`),
    CONSTRAINT `chk_academic_role_code`
        CHECK (`role_code` IN ('FULL_TIME_TEACHER', 'MAJOR_LEADER', 'VICE_DEAN', 'DEAN', 'PARTY_SECRETARY')),
    CONSTRAINT `chk_academic_role_scope`
        CHECK ((`role_code` = 'MAJOR_LEADER' AND `major_id` IS NOT NULL)
            OR (`role_code` <> 'MAJOR_LEADER' AND `major_id` IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `academic_role_assignment` (`user_id`, `role_code`, `major_id`)
SELECT `id`, 'FULL_TIME_TEACHER', NULL
FROM `user`;

ALTER TABLE `certificate`
    ADD COLUMN `competition_category` VARCHAR(32) NOT NULL DEFAULT 'SECOND_CLASS'
        AFTER `certificate_type`,
    ADD CONSTRAINT `chk_certificate_competition_category`
        CHECK (`competition_category` IN ('FIRST_CLASS', 'SECOND_CLASS'));
