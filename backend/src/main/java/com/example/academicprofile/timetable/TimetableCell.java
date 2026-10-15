package com.example.academicprofile.timetable;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("timetable_cell")
public class TimetableCell {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long batchId;
    private Long teacherId;
    private Integer dayOfWeek;
    private String slotCode;
    private Integer weekStart;
    private Integer weekEnd;
    private String courseName;
    private String className;
    private String roomName;
    private String occupancyText;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public Long getTeacherId() { return teacherId; }
    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }
    public Integer getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(Integer dayOfWeek) { this.dayOfWeek = dayOfWeek; }
    public String getSlotCode() { return slotCode; }
    public void setSlotCode(String slotCode) { this.slotCode = slotCode; }
    public Integer getWeekStart() { return weekStart; }
    public void setWeekStart(Integer weekStart) { this.weekStart = weekStart; }
    public Integer getWeekEnd() { return weekEnd; }
    public void setWeekEnd(Integer weekEnd) { this.weekEnd = weekEnd; }
    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }
    public String getOccupancyText() { return occupancyText; }
    public void setOccupancyText(String occupancyText) { this.occupancyText = occupancyText; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
