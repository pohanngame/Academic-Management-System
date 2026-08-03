CREATE TABLE `resource_access_policy` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `owner_teacher_id` BIGINT NOT NULL,
    `resource_type` VARCHAR(32) NOT NULL,
    `resource_id` BIGINT NOT NULL,
    `view_allowed` TINYINT(1) NOT NULL DEFAULT 1,
    `download_allowed` TINYINT(1) NOT NULL DEFAULT 0,
    `ai_use_allowed` TINYINT(1) NOT NULL DEFAULT 0,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_resource_policy_type_id` (`resource_type`, `resource_id`),
    KEY `idx_resource_policy_owner_type` (`owner_teacher_id`, `resource_type`, `resource_id`),
    CONSTRAINT `fk_resource_policy_owner`
        FOREIGN KEY (`owner_teacher_id`) REFERENCES `teacher_profile` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `resource_access_policy`
    (`owner_teacher_id`, `resource_type`, `resource_id`, `view_allowed`, `download_allowed`, `ai_use_allowed`)
SELECT `teacher_id`, 'ACADEMIC_QUALIFICATION', `id`, 1, 0, 0 FROM `academic_qualification`
UNION ALL SELECT `teacher_id`, 'TEACHING_AREA', `id`, 1, 0, 0 FROM `teaching_area`
UNION ALL SELECT `teacher_id`, 'RESEARCH_AREA', `id`, 1, 0, 0 FROM `research_area`
UNION ALL SELECT `teacher_id`, 'PROFESSIONAL_SERVICE', `id`, 1, 0, 0 FROM `professional_service`
UNION ALL SELECT `teacher_id`, 'WORKING_EXPERIENCE', `id`, 1, 0, 0 FROM `working_experience`
UNION ALL SELECT `teacher_id`, 'PROJECT', `id`, 1, 0, 0 FROM `project`
UNION ALL SELECT `teacher_id`, 'TEACHING_COURSE', `id`, 1, 0, 0 FROM `teaching_course`
UNION ALL SELECT `teacher_id`, 'PAPER', `id`, 1, 0, 0 FROM `paper`
UNION ALL SELECT `teacher_id`, 'PATENT', `id`, 1, 0, 0 FROM `patent`
UNION ALL SELECT `teacher_id`, 'CERTIFICATE', `id`, 1, 0, 0 FROM `certificate`
UNION ALL SELECT `teacher_id`, 'FILE_ATTACHMENT', `id`, 1, 0, 0
    FROM `file_metadata`
    WHERE `business_type` <> 'AVATAR' AND `deleted` = 0;

CREATE TABLE `resource_access_request` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `requester_teacher_id` BIGINT NOT NULL,
    `owner_teacher_id` BIGINT NOT NULL,
    `resource_type` VARCHAR(32) NOT NULL,
    `permission_type` VARCHAR(16) NOT NULL,
    `purpose` VARCHAR(1000) NOT NULL,
    `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    `requested_expires_at` DATETIME NOT NULL,
    `decided_at` DATETIME NULL,
    `decision_comment` VARCHAR(1000) NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_access_request_requester` (`requester_teacher_id`, `status`, `created_at`),
    KEY `idx_access_request_owner` (`owner_teacher_id`, `status`, `created_at`),
    CONSTRAINT `fk_access_request_requester`
        FOREIGN KEY (`requester_teacher_id`) REFERENCES `teacher_profile` (`id`),
    CONSTRAINT `fk_access_request_owner`
        FOREIGN KEY (`owner_teacher_id`) REFERENCES `teacher_profile` (`id`),
    CONSTRAINT `chk_access_request_permission`
        CHECK (`permission_type` IN ('VIEW', 'DOWNLOAD', 'AI_USE')),
    CONSTRAINT `chk_access_request_status`
        CHECK (`status` IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `resource_access_request_item` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `request_id` BIGINT NOT NULL,
    `resource_type` VARCHAR(32) NOT NULL,
    `resource_id` BIGINT NOT NULL,
    `approved` TINYINT(1) NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_access_request_item` (`request_id`, `resource_type`, `resource_id`),
    KEY `idx_access_request_item_resource` (`resource_type`, `resource_id`),
    CONSTRAINT `fk_access_request_item_request`
        FOREIGN KEY (`request_id`) REFERENCES `resource_access_request` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `resource_access_grant` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `request_id` BIGINT NOT NULL,
    `requester_teacher_id` BIGINT NOT NULL,
    `owner_teacher_id` BIGINT NOT NULL,
    `permission_type` VARCHAR(16) NOT NULL,
    `resource_type` VARCHAR(32) NOT NULL,
    `resource_id` BIGINT NOT NULL,
    `purpose` VARCHAR(1000) NOT NULL,
    `effective_at` DATETIME NOT NULL,
    `expires_at` DATETIME NOT NULL,
    `revoked_at` DATETIME NULL,
    `revoked_by_teacher_id` BIGINT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_access_grant_request_resource` (`request_id`, `resource_type`, `resource_id`),
    KEY `idx_access_grant_lookup`
        (`requester_teacher_id`, `resource_type`, `resource_id`, `permission_type`, `expires_at`),
    KEY `idx_access_grant_owner` (`owner_teacher_id`, `expires_at`, `revoked_at`),
    CONSTRAINT `fk_access_grant_request`
        FOREIGN KEY (`request_id`) REFERENCES `resource_access_request` (`id`),
    CONSTRAINT `fk_access_grant_requester`
        FOREIGN KEY (`requester_teacher_id`) REFERENCES `teacher_profile` (`id`),
    CONSTRAINT `fk_access_grant_owner`
        FOREIGN KEY (`owner_teacher_id`) REFERENCES `teacher_profile` (`id`),
    CONSTRAINT `fk_access_grant_revoker`
        FOREIGN KEY (`revoked_by_teacher_id`) REFERENCES `teacher_profile` (`id`),
    CONSTRAINT `chk_access_grant_permission`
        CHECK (`permission_type` IN ('VIEW', 'DOWNLOAD', 'AI_USE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `resource_access_audit` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `actor_user_id` BIGINT NOT NULL,
    `actor_teacher_id` BIGINT NOT NULL,
    `owner_teacher_id` BIGINT NOT NULL,
    `operation_type` VARCHAR(32) NOT NULL,
    `resource_type` VARCHAR(32) NULL,
    `resource_id` BIGINT NULL,
    `file_id` BIGINT NULL,
    `request_id` BIGINT NULL,
    `grant_id` BIGINT NULL,
    `purpose` VARCHAR(1000) NULL,
    `success` TINYINT(1) NOT NULL,
    `ip_address` VARCHAR(64) NULL,
    `client_info` VARCHAR(255) NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_access_audit_owner_created` (`owner_teacher_id`, `created_at`),
    KEY `idx_access_audit_actor_created` (`actor_teacher_id`, `created_at`),
    KEY `idx_access_audit_resource` (`resource_type`, `resource_id`, `created_at`),
    CONSTRAINT `chk_access_audit_operation`
        CHECK (`operation_type` IN (
            'LIST', 'DETAIL', 'PREVIEW', 'DOWNLOAD', 'AI_USE',
            'POLICY_UPDATE', 'REQUEST_CREATE', 'REQUEST_APPROVE',
            'REQUEST_REJECT', 'REQUEST_CANCEL', 'GRANT_REVOKE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
