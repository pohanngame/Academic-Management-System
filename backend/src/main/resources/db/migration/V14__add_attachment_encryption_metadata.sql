ALTER TABLE `file_metadata`
    ADD COLUMN `encryption_version` VARCHAR(32) NULL AFTER `business_id`,
    ADD COLUMN `encryption_key_id` VARCHAR(32) NULL AFTER `encryption_version`,
    ADD COLUMN `wrapped_dek` TEXT NULL AFTER `encryption_key_id`,
    ADD COLUMN `ciphertext_status` VARCHAR(16) NOT NULL DEFAULT 'PLAINTEXT' AFTER `wrapped_dek`,
    ADD COLUMN `plaintext_sha256` VARCHAR(64) NULL AFTER `ciphertext_status`,
    ADD COLUMN `legacy_storage_path` VARCHAR(1024) NULL AFTER `plaintext_sha256`,
    ADD KEY `idx_file_metadata_ciphertext_status` (`ciphertext_status`, `id`),
    ADD CONSTRAINT `chk_file_ciphertext_status`
        CHECK (`ciphertext_status` IN ('PLAINTEXT', 'MIGRATING', 'ENCRYPTED'));

ALTER TABLE `ai_generation_task`
    ADD COLUMN `source_manifest` JSON NULL AFTER `selected_modules`;
