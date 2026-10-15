package com.example.academicprofile.timetable;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class TimetableDtos {
    private TimetableDtos() {
    }

    public record TeacherLinkRequest(
            @NotBlank @Size(max = 128) String sourceName,
            @NotNull Long teacherId) {
    }

    public record TeacherLinkResponse(
            Long id,
            String sourceName,
            Long teacherId,
            String teacherDisplayName) {
    }

    public record CaptchaRequest(@NotBlank String username) {
    }

    public record CaptchaResponse(
            String attemptId,
            boolean captchaRequired,
            String captchaImageBase64,
            boolean sliderRequired,
            String sliderBackgroundBase64,
            String sliderPieceBase64) {
    }

    public record SyncRequest(
            @NotBlank String attemptId,
            @NotBlank @Size(max = 32) String semesterCode,
            @NotBlank String password,
            String captcha,
            Integer sliderCanvasWidth,
            Integer sliderMove,
            List<Map<String, Object>> sliderTracks) {
    }

    public record SyncResponse(
            Long batchId,
            String semesterCode,
            int teacherCount,
            int cellCount,
            List<String> unmappedNames) {
    }

    /** 公共空闲查询结果。slots 固定 42 项（7 天 × 6 节次组）。 */
    public record CommonFreeResponse(
            String semesterCode,
            int week,
            boolean missingImport,
            List<CommonFreeSlot> slots,
            List<ExcludedTeacher> excludedTeachers) {
    }

    /** 某一格是否空闲；busy 时带上占用明细便于展示。 */
    public record CommonFreeSlot(
            int dayOfWeek,
            String slotCode,
            boolean free,
            List<OccupancyEntry> occupancies) {
    }

    public record OccupancyEntry(
            Long teacherId,
            String teacherDisplayName,
            String courseName,
            String roomName,
            String occupancyText) {
    }

    /** 未映射到 teacher_profile 的教务显示名：不参与空闲判断，单列出来。 */
    public record ExcludedTeacher(String sourceName, String reason) {
    }
}
