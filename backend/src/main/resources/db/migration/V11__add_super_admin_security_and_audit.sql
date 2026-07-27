ALTER TABLE `user`
    ADD COLUMN `auth_version` BIGINT NOT NULL DEFAULT 0 AFTER `status`,
    ADD CONSTRAINT `chk_user_role`
        CHECK (`role` IN ('TEACHER', 'SUPER_ADMIN')),
    ADD CONSTRAINT `chk_user_status`
        CHECK (`status` IN ('ACTIVE', 'DISABLED'));

CREATE TABLE `admin_audit_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `operator_user_id` BIGINT NOT NULL,
    `action_type` VARCHAR(64) NOT NULL,
    `target_user_id` BIGINT NULL,
    `success` TINYINT(1) NOT NULL,
    `summary` VARCHAR(512) NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_admin_audit_created_at` (`created_at`),
    KEY `idx_admin_audit_operator` (`operator_user_id`, `created_at`),
    KEY `idx_admin_audit_action` (`action_type`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
