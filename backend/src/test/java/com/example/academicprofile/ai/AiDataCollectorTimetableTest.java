package com.example.academicprofile.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.academicprofile.access.ResourceAccessAuditService;
import com.example.academicprofile.access.ResourceAuthorizationService;
import com.example.academicprofile.achievement.AchievementService;
import com.example.academicprofile.file.FileMetadataMapper;
import com.example.academicprofile.ocr.OcrTaskMapper;
import com.example.academicprofile.profileblock.ProfileBlockService;
import com.example.academicprofile.security.CurrentUser;
import com.example.academicprofile.teacher.TeacherProfileService;
import com.example.academicprofile.timetable.TimetableCommonFreeService;
import com.example.academicprofile.timetable.TimetableDtos.CommonFreeResponse;
import com.example.academicprofile.timetable.TimetableDtos.CommonFreeSlot;
import com.example.academicprofile.timetable.TimetableDtos.OccupancyEntry;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * AI 课表模块的取舍规则：只有本次请求选中 timetableOccupancy 时才带课表；
 * 未选中时载荷不得含课表。
 */
class AiDataCollectorTimetableTest {

    private TimetableCommonFreeService commonFree;
    private TeacherProfileService profileService;
    private AiDataCollector collector;

    @BeforeEach
    void setUp() {
        commonFree = mock(TimetableCommonFreeService.class);
        profileService = mock(TeacherProfileService.class);
        collector = new AiDataCollector(
                profileService,
                mock(ProfileBlockService.class),
                mock(AchievementService.class),
                mock(ResourceAuthorizationService.class),
                mock(ResourceAccessAuditService.class),
                new ObjectMapper(),
                mock(FileMetadataMapper.class),
                mock(OcrTaskMapper.class),
                commonFree);
    }

    private static com.example.academicprofile.teacher.TeacherProfile profile() {
        com.example.academicprofile.teacher.TeacherProfile profile =
                new com.example.academicprofile.teacher.TeacherProfile();
        profile.setId(7L);
        profile.setDisplayName("张三");
        return profile;
    }

    private static CommonFreeResponse responseWithOccupancy() {
        OccupancyEntry entry = new OccupancyEntry(7L, "张三", "数据结构", "教一101", "数据结构\r\n计科2301\r\n(1-16周)\r\n教一101");
        CommonFreeSlot busy = new CommonFreeSlot(2, "0304", false, List.of(entry));
        CommonFreeSlot free = new CommonFreeSlot(2, "0506", true, List.of());
        return new CommonFreeResponse("2026-2027-1", 5, false, List.of(busy, free), List.of());
    }

    private static CurrentUser actor() {
        return new CurrentUser(1L, 7L, "teacher", "t@example.com", "TEACHER", 0L);
    }

    private static AiGenerationRequest request(
            List<String> modules, AiGenerationRequest.TimetableQuery query) {
        return new AiGenerationRequest(
                null, "生成要求", modules, null, null, null, null, null, "AI_DRAFT", null, null, query);
    }

    @Test
    void unselectedModuleNeverIncludesTimetableInThePayload() {
        when(commonFree.query(anyString(), anyInt(), any(), any())).thenReturn(responseWithOccupancy());
        when(profileService.getByTeacherId(any())).thenReturn(profile());

        Map<String, Object> data = collector.collect(
                actor(), List.of("teacherProfile"),
                request(List.of("teacherProfile"), null)).data();

        assertThat(data).doesNotContainKey("timetableOccupancy");
        // 未选中就不该去查课表。
        verify(commonFree, never()).query(anyString(), anyInt(), any(), any());
    }

    @Test
    void selectedModuleIncludesOnlyTheCurrentQueryResult() {
        when(commonFree.query(anyString(), anyInt(), any(), any())).thenReturn(responseWithOccupancy());

        Map<String, Object> data = collector.collect(
                actor(), List.of("timetableOccupancy"),
                request(List.of("timetableOccupancy"),
                        new AiGenerationRequest.TimetableQuery("2026-2027-1", 5, 3L, null))).data();

        assertThat(data).containsKey("timetableOccupancy");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) data.get("timetableOccupancy");
        // 只带占用，不带空闲格。
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("teacherName", "张三")
                .containsEntry("courseName", "数据结构")
                .containsEntry("roomName", "教一101")
                .containsEntry("dayOfWeek", 2)
                .containsEntry("slotCode", "0304")
                .containsEntry("week", 5);
        verify(commonFree).query("2026-2027-1", 5, 3L, List.of());
    }

    @Test
    void selectedModuleWithoutQueryParametersAddsNoRowsAndNeverCallsTheService() {
        Map<String, Object> data = collector.collect(
                actor(), List.of("timetableOccupancy"),
                request(List.of("timetableOccupancy"), null)).data();

        assertThat(data).containsKey("timetableOccupancy");
        assertThat((List<?>) data.get("timetableOccupancy")).isEmpty();
        verify(commonFree, never()).query(anyString(), anyInt(), any(), any());
    }
}
