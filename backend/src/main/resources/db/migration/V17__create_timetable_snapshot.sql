CREATE TABLE `timetable_teacher_link` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `source_name` VARCHAR(128) NOT NULL,
    `teacher_id` BIGINT NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_timetable_teacher_link_name` (`source_name`),
    UNIQUE KEY `uk_timetable_teacher_link_teacher` (`teacher_id`),
    CONSTRAINT `fk_timetable_teacher_link_teacher`
        FOREIGN KEY (`teacher_id`) REFERENCES `teacher_profile` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `timetable_import_batch` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `semester_code` VARCHAR(32) NOT NULL,
    `created_by` BIGINT NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_timetable_import_semester` (`semester_code`, `id`),
    CONSTRAINT `fk_timetable_import_batch_user`
        FOREIGN KEY (`created_by`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `timetable_cell` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `batch_id` BIGINT NOT NULL,
    `teacher_id` BIGINT NOT NULL,
    `day_of_week` TINYINT NOT NULL,
    `slot_code` CHAR(4) NOT NULL,
    `week_start` INT NOT NULL,
    `week_end` INT NOT NULL,
    `course_name` VARCHAR(255) NOT NULL,
    `class_name` VARCHAR(255) NULL,
    `room_name` VARCHAR(255) NULL,
    `occupancy_text` VARCHAR(1000) NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_timetable_cell_query` (`batch_id`, `teacher_id`, `day_of_week`, `slot_code`, `week_start`, `week_end`),
    CONSTRAINT `fk_timetable_cell_batch`
        FOREIGN KEY (`batch_id`) REFERENCES `timetable_import_batch` (`id`),
    CONSTRAINT `fk_timetable_cell_teacher`
        FOREIGN KEY (`teacher_id`) REFERENCES `teacher_profile` (`id`),
    CONSTRAINT `chk_timetable_cell_day`
        CHECK (`day_of_week` BETWEEN 1 AND 7),
    CONSTRAINT `chk_timetable_cell_slot`
        CHECK (`slot_code` IN ('0102', '0304', '0506', '0708', '0910', '1112')),
    CONSTRAINT `chk_timetable_cell_week`
        CHECK (`week_start` BETWEEN 1 AND 30 AND `week_end` BETWEEN `week_start` AND 30)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
