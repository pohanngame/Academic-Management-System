package com.example.academicprofile.timetable;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 一次导入里出现过的已映射教师。整学期无课的教师也有本行记录，
 * 用于区分“已映射但本学期总表没有该教师”（ABSENT_FROM_IMPORT）。
 * 主键是 (batch_id, teacher_id)，这里以 batchId 作为 MyBatis-Plus 的标识列。
 */
@TableName("timetable_import_teacher")
public class TimetableImportTeacher {
    @TableId(value = "batch_id", type = IdType.INPUT)
    private Long batchId;
    private Long teacherId;
    private LocalDateTime createdAt;

    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public Long getTeacherId() { return teacherId; }
    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
