package com.example.academicprofile.ocr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.academicprofile.access.ResourcePolicyService;
import com.example.academicprofile.achievement.Certificate;
import com.example.academicprofile.achievement.CertificateMapper;
import com.example.academicprofile.achievement.CompetitionCategory;
import com.example.academicprofile.achievement.PaperMapper;
import com.example.academicprofile.achievement.PatentMapper;
import com.example.academicprofile.file.FileBusinessType;
import com.example.academicprofile.file.FileMetadata;
import com.example.academicprofile.file.FileStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;

class OcrQuickCertificateServiceTest {
    @Test
    void stagingConfirmationCreatesOneCertificateAttachesEncryptedFileAndIsIdempotent() {
        OcrTaskMapper tasks = mock(OcrTaskMapper.class);
        OcrResultMapper results = mock(OcrResultMapper.class);
        FileStorageService files = mock(FileStorageService.class);
        CertificateMapper certificates = mock(CertificateMapper.class);
        ResourcePolicyService policies = mock(ResourcePolicyService.class);
        OcrTask task = new OcrTask();
        task.setId(10L); task.setTeacherId(7L); task.setFileId(20L);
        task.setTargetType("CERTIFICATE"); task.setStatus("SUCCEEDED");
        OcrResult result = new OcrResult();
        result.setId(30L); result.setTaskId(10L); result.setTeacherId(7L);
        result.setTargetType("CERTIFICATE"); result.setStatus("DRAFT"); result.setCertificateName("测试证书");
        FileMetadata staging = new FileMetadata();
        staging.setId(20L); staging.setTeacherId(7L); staging.setBusinessType(FileBusinessType.CERTIFICATE_OCR_STAGING.name());
        when(tasks.selectByIdForUpdate(10L)).thenReturn(task);
        when(tasks.selectById(10L)).thenReturn(task);
        when(results.selectList(any())).thenReturn(List.of(result));
        when(certificates.selectList(any())).thenReturn(List.of());
        when(certificates.insert(any(Certificate.class))).thenAnswer(invocation -> {
            ((Certificate) invocation.getArgument(0)).setId(40L); return 1;
        });
        when(files.getOwnedFileMetadata(7L, 20L)).thenReturn(staging);

        OcrService service = new OcrService(tasks, results, files, new OcrProperties(),
                mock(PaddleOcrClient.class), mock(OcrCandidateExtractor.class),
                mock(PaperMapper.class), mock(PatentMapper.class), certificates,
                new ObjectMapper(), policies);

        OcrConfirmResponse first = service.confirmTask(7L, 10L,
                new OcrConfirmRequest(List.of(30L), CompetitionCategory.FIRST_CLASS));
        OcrConfirmResponse duplicate = service.confirmTask(7L, 10L,
                new OcrConfirmRequest(List.of(30L), CompetitionCategory.FIRST_CLASS));

        assertThat(first.confirmedCount()).isEqualTo(1);
        assertThat(duplicate.confirmedCount()).isZero();
        verify(certificates, times(1)).insert(any(Certificate.class));
        verify(files, times(1)).attachCertificateOcrStaging(7L, 20L, 40L);
    }
}
