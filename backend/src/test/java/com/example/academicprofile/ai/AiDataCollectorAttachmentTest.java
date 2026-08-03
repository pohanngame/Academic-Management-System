package com.example.academicprofile.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.academicprofile.access.AccessPermission;
import com.example.academicprofile.access.ResourceAccessAuditService;
import com.example.academicprofile.access.ResourceAuthorizationService;
import com.example.academicprofile.access.ResourceType;
import com.example.academicprofile.achievement.AchievementService;
import com.example.academicprofile.common.exception.BusinessException;
import com.example.academicprofile.file.FileBusinessType;
import com.example.academicprofile.file.FileMetadata;
import com.example.academicprofile.file.FileMetadataMapper;
import com.example.academicprofile.ocr.OcrTask;
import com.example.academicprofile.ocr.OcrTaskMapper;
import com.example.academicprofile.profileblock.ProfileBlockService;
import com.example.academicprofile.security.CurrentUser;
import com.example.academicprofile.teacher.TeacherProfileService;
import com.fasterxml.jackson.databind.ObjectMapper;

class AiDataCollectorAttachmentTest {
    private ResourceAuthorizationService authorization;
    private ResourceAccessAuditService audits;
    private FileMetadataMapper files;
    private OcrTaskMapper ocrTasks;
    private AiDataCollector collector;

    @BeforeEach
    void setUp() {
        authorization = mock(ResourceAuthorizationService.class);
        audits = mock(ResourceAccessAuditService.class);
        files = mock(FileMetadataMapper.class);
        ocrTasks = mock(OcrTaskMapper.class);
        collector = new AiDataCollector(
                mock(TeacherProfileService.class),
                mock(ProfileBlockService.class),
                mock(AchievementService.class),
                authorization,
                audits,
                new ObjectMapper(),
                files,
                ocrTasks);
    }

    @Test
    void confirmedOwnedAttachmentIsIncludedAndAudited() {
        FileMetadata file = file(31L, 7L);
        when(files.selectById(31L)).thenReturn(file);
        when(authorization.require(any(), any(), any(), any()))
                .thenReturn(new ResourceAuthorizationService.AccessDecision(7L, null));
        OcrTask confirmed = new OcrTask();
        confirmed.setStatus("CONFIRMED");
        confirmed.setRawText("confirmed synthetic text");
        when(ocrTasks.selectOne(any(Wrapper.class))).thenReturn(confirmed);

        var result = collector.collect(actor(7L), List.of(), request(List.of(31L)));

        assertThat(result.data().get("attachments").toString()).contains("confirmed synthetic text");
        assertThat(result.sources()).singleElement().satisfies(source -> {
            assertThat(source.fileId()).isEqualTo(31L);
            assertThat(source.resourceId()).isEqualTo(10L);
        });
        verify(authorization).require(
                any(), org.mockito.ArgumentMatchers.eq(ResourceType.FILE_ATTACHMENT),
                org.mockito.ArgumentMatchers.eq(31L),
                org.mockito.ArgumentMatchers.eq(AccessPermission.AI_USE));
        verify(audits).record(any(), org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq("AI_USE"),
                org.mockito.ArgumentMatchers.eq(ResourceType.FILE_ATTACHMENT),
                org.mockito.ArgumentMatchers.eq(31L),
                org.mockito.ArgumentMatchers.eq(31L), any(), org.mockito.ArgumentMatchers.eq(true),
                any(), any());
    }

    @Test
    void unconfirmedAttachmentFailsBeforeReturningPromptData() {
        when(files.selectById(31L)).thenReturn(file(31L, 7L));
        when(authorization.require(any(), any(), any(), any()))
                .thenReturn(new ResourceAuthorizationService.AccessDecision(7L, null));
        when(ocrTasks.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> collector.collect(actor(7L), List.of(), request(List.of(31L))))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getMessage()).contains("OCR"));
    }

    private AiGenerationRequest request(List<Long> attachmentIds) {
        return new AiGenerationRequest(
                null, "test", List.of("teacherProfile"), null, null, null,
                null, null, "AI_DRAFT", attachmentIds);
    }

    private FileMetadata file(Long id, Long owner) {
        FileMetadata file = new FileMetadata();
        file.setId(id);
        file.setTeacherId(owner);
        file.setBusinessType(FileBusinessType.PAPER.name());
        file.setBusinessId(10L);
        file.setOriginalName("synthetic.pdf");
        file.setDeleted(false);
        return file;
    }

    private CurrentUser actor(Long teacherId) {
        return new CurrentUser(1L, teacherId, "teacher", "t@example.test", "TEACHER", 0L);
    }
}
