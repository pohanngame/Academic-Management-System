-- 增量 V18。记录一次导入里出现过的已映射教师，包括整学期没有课的教师。
-- 空单元格仍不写入 timetable_cell。
CREATE TABLE `timetable_import_teacher` (
    `batch_id` BIGINT NOT NULL,
    `teacher_id` BIGINT NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`batch_id`, `teacher_id`),
    CONSTRAINT `fk_timetable_import_teacher_batch`
        FOREIGN KEY (`batch_id`) REFERENCES `timetable_import_batch` (`id`),
    CONSTRAINT `fk_timetable_import_teacher_teacher`
        FOREIGN KEY (`teacher_id`) REFERENCES `teacher_profile` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
