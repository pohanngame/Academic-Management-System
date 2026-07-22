package com.example.academicprofile.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;

import com.example.academicprofile.common.exception.BusinessException;
import com.example.academicprofile.file.FileDownload;
import com.example.academicprofile.file.FileBusinessType;
import com.example.academicprofile.file.FileMetadata;
import com.example.academicprofile.file.FileStorageService;
import com.example.academicprofile.pdf.PdfConversionClient;
import com.example.academicprofile.word.WordTemplateService;
import com.example.academicprofile.word.WordTemplateServiceTest;
import com.fasterxml.jackson.databind.ObjectMapper;

class AiGenerationServiceTest {

    private AiGenerationTaskMapper taskMapper;
    private AiGenerationResultMapper resultMapper;
    private AiDataCollector dataCollector;
    private AiChatClient chatClient;
    private FileStorageService fileStorageService;
    private WordTemplateService wordTemplateService;
    private PdfConversionClient pdfConversionClient;
    private AiProperties properties;
    private AiGenerationService service;

    @BeforeEach
    void setUp() {
        taskMapper = mock(AiGenerationTaskMapper.class);
        resultMapper = mock(AiGenerationResultMapper.class);
        dataCollector = mock(AiDataCollector.class);
        chatClient = mock(AiChatClient.class);
        fileStorageService = mock(FileStorageService.class);
        wordTemplateService = new WordTemplateService();
        pdfConversionClient = mock(PdfConversionClient.class);
        properties = new AiProperties();
        properties.setProvider("local-mock");
        properties.setModel("mock-model");
        service = new AiGenerationService(
                taskMapper,
                resultMapper,
                properties,
                dataCollector,
                chatClient,
                new ObjectMapper(),
                fileStorageService,
                wordTemplateService,
                pdfConversionClient);
    }

    @Test
    void templateTaskAllowsEmptyRequirementAndOmitsEmptyPromptSection() throws IOException {
        byte[] templateBytes = WordTemplateServiceTest.documentWithSplitPlaceholder(false);
        FileMetadata metadata = templateMetadata(false);
        when(fileStorageService.readActiveAiWordTemplate(1L, 10L))
                .thenReturn(new FileDownload(metadata, new ByteArrayResource(templateBytes)));
        when(fileStorageService.getReferencedAiWordTemplateMetadata(1L, 10L)).thenReturn(metadata);
        when(dataCollector.collect(1L, List.of("teacherProfile")))
                .thenReturn(Map.of("teacherProfile", Map.of("displayName", "测试教师")));
        when(chatClient.generate(anyString())).thenReturn("本地 mock 草稿");

        AtomicReference<AiGenerationTask> savedTask = new AtomicReference<>();
        AtomicReference<AiGenerationResult> savedResult = new AtomicReference<>();
        when(taskMapper.insert(any(AiGenerationTask.class))).thenAnswer(invocation -> {
            AiGenerationTask task = invocation.getArgument(0);
            task.setId(100L);
            savedTask.set(task);
            return 1;
        });
        when(resultMapper.insert(any(AiGenerationResult.class))).thenAnswer(invocation -> {
            AiGenerationResult result = invocation.getArgument(0);
            result.setId(200L);
            savedResult.set(result);
            return 1;
        });
        when(taskMapper.selectById(100L)).thenAnswer(invocation -> savedTask.get());
        when(resultMapper.selectOne(any())).thenAnswer(invocation -> savedResult.get());

        AiGenerationTaskResponse response = service.createTask(
                1L,
                new AiGenerationRequest(10L, "   ", List.of("teacherProfile")));

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatClient).generate(promptCaptor.capture());
        String prompt = promptCaptor.getValue();
        assertTrue(prompt.contains("项目申报个人材料"));
        assertTrue(prompt.contains("代表成果"));
        assertTrue(prompt.indexOf("项目申报个人材料") < prompt.indexOf("代表成果"));
        assertFalse(prompt.contains(WordTemplateService.PLACEHOLDER));
        assertFalse(prompt.contains("补充要求："));
        assertEquals("", savedTask.get().getTemplateRequirement());
        assertEquals(10L, response.templateFileId());
        assertEquals("template.docx", response.templateFileName());
    }

    @Test
    void textOnlyTaskStillRequiresRequirement() {
        assertThrows(
                BusinessException.class,
                () -> service.createTask(1L, new AiGenerationRequest(null, " ", List.of("teacherProfile"))));
        verify(taskMapper, never()).insert(any(AiGenerationTask.class));
        verify(chatClient, never()).generate(anyString());
    }

    @Test
    void oversizedPromptIsRejectedBeforeCreatingTaskOrCallingAi() {
        properties.setMaxPromptChars(100);
        when(dataCollector.collect(1L, List.of("teacherProfile")))
                .thenReturn(Map.of("teacherProfile", Map.of("displayName", "测试教师")));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.createTask(1L, new AiGenerationRequest(null, "简短要求", List.of("teacherProfile"))));

        assertTrue(exception.getMessage().contains("character limit"));
        verify(taskMapper, never()).insert(any(AiGenerationTask.class));
        verify(chatClient, never()).generate(anyString());
    }

    @Test
    void deletedTemplateHistoricalExportUsesInternalReadAndChecksTeacher() throws IOException {
        AiGenerationTask task = confirmedTask(1L, 10L);
        AiGenerationResult result = confirmedResult(1L);
        when(taskMapper.selectById(100L)).thenReturn(task);
        when(resultMapper.selectOne(any())).thenReturn(result);
        FileMetadata metadata = templateMetadata(true);
        byte[] templateBytes = WordTemplateServiceTest.documentWithSplitPlaceholder(false);
        when(fileStorageService.readReferencedAiWordTemplate(1L, 10L))
                .thenReturn(new FileDownload(metadata, new ByteArrayResource(templateBytes)));

        AiGeneratedFile file = service.exportWord(1L, 100L);

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(file.content()))) {
            String text = document.getParagraphs().stream().map(paragraph -> paragraph.getText()).reduce("", String::concat)
                    + document.getTables().stream().map(table -> table.getText()).reduce("", String::concat);
            assertTrue(text.contains("项目申报个人材料"));
            assertTrue(text.contains("确认后的本地 mock 内容"));
            assertFalse(text.contains(WordTemplateService.PLACEHOLDER));
        }
        verify(fileStorageService).readReferencedAiWordTemplate(1L, 10L);

        assertThrows(BusinessException.class, () -> service.exportWord(2L, 100L));
    }

    @Test
    void confirmedTemplateTaskCanExportPdfThroughConverter() throws IOException {
        AiGenerationTask task = confirmedTask(1L, 10L);
        AiGenerationResult result = confirmedResult(1L);
        when(taskMapper.selectById(100L)).thenReturn(task);
        when(resultMapper.selectOne(any())).thenReturn(result);
        FileMetadata metadata = templateMetadata(true);
        byte[] templateBytes = WordTemplateServiceTest.documentWithSplitPlaceholder(false);
        when(fileStorageService.readReferencedAiWordTemplate(1L, 10L))
                .thenReturn(new FileDownload(metadata, new ByteArrayResource(templateBytes)));
        byte[] pdf = "%PDF-mock".getBytes();
        when(pdfConversionClient.convertDocx(any(byte[].class), eq(100L))).thenReturn(pdf);

        AiGeneratedFile file = service.exportPdf(1L, 100L);

        assertEquals("application/pdf", file.contentType());
        assertTrue(file.fileName().endsWith(".pdf"));
        assertArrayEquals(pdf, file.content());
        verify(pdfConversionClient).convertDocx(any(byte[].class), eq(100L));
        verify(chatClient, never()).generate(anyString());
        verify(taskMapper, never()).updateById(any(AiGenerationTask.class));
        verify(resultMapper, never()).updateById(any(AiGenerationResult.class));
    }

    @Test
    void unconfirmedTaskDoesNotCallPdfConverter() {
        AiGenerationTask task = confirmedTask(1L, null);
        task.setStatus("SUCCEEDED");
        AiGenerationResult result = confirmedResult(1L);
        result.setStatus("DRAFT");
        when(taskMapper.selectById(100L)).thenReturn(task);
        when(resultMapper.selectOne(any())).thenReturn(result);

        assertThrows(BusinessException.class, () -> service.exportPdf(1L, 100L));

        verify(pdfConversionClient, never()).convertDocx(any(byte[].class), anyLong());
    }

    @Test
    void teacherIsolationRejectsPdfBeforeCallingConverter() {
        when(taskMapper.selectById(100L)).thenReturn(confirmedTask(1L, null));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.exportPdf(2L, 100L));

        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, exception.getStatus());
        verify(pdfConversionClient, never()).convertDocx(any(byte[].class), anyLong());
    }

    @Test
    void confirmedNoTemplateTaskCanExportPdfThroughConverter() throws IOException {
        AiGenerationTask task = confirmedTask(1L, null);
        AiGenerationResult result = confirmedResult(1L);
        when(taskMapper.selectById(100L)).thenReturn(task);
        when(resultMapper.selectOne(any())).thenReturn(result);
        byte[] pdf = "%PDF-mock".getBytes();
        when(pdfConversionClient.convertDocx(any(byte[].class), eq(100L))).thenReturn(pdf);

        AiGeneratedFile file = service.exportPdf(1L, 100L);

        ArgumentCaptor<byte[]> wordCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(pdfConversionClient).convertDocx(wordCaptor.capture(), eq(100L));
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(wordCaptor.getValue()))) {
            String text = document.getParagraphs().stream()
                    .map(paragraph -> paragraph.getText())
                    .reduce("", String::concat);
            assertTrue(text.contains("AI 生成材料"));
            assertTrue(text.contains("确认后的本地 mock 内容"));
        }
        assertArrayEquals(pdf, file.content());
        verify(fileStorageService, never()).readReferencedAiWordTemplate(any(), any());
    }

    @Test
    void originalNoTemplateWordExportStillWorks() throws IOException {
        AiGenerationTask task = confirmedTask(1L, null);
        AiGenerationResult result = confirmedResult(1L);
        when(taskMapper.selectById(100L)).thenReturn(task);
        when(resultMapper.selectOne(any())).thenReturn(result);

        AiGeneratedFile file = service.exportWord(1L, 100L);

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(file.content()))) {
            String text = document.getParagraphs().stream().map(paragraph -> paragraph.getText()).reduce("", String::concat);
            assertTrue(text.contains("AI 生成材料"));
            assertTrue(text.contains("确认后的本地 mock 内容"));
        }
        verify(fileStorageService, never()).readReferencedAiWordTemplate(any(), any());
    }

    private FileMetadata templateMetadata(boolean deleted) {
        FileMetadata metadata = new FileMetadata();
        metadata.setId(10L);
        metadata.setTeacherId(1L);
        metadata.setOriginalName("template.docx");
        metadata.setBusinessType(FileBusinessType.AI_WORD_TEMPLATE.name());
        metadata.setDeleted(deleted);
        return metadata;
    }

    private AiGenerationTask confirmedTask(Long teacherId, Long templateFileId) {
        AiGenerationTask task = new AiGenerationTask();
        task.setId(100L);
        task.setTeacherId(teacherId);
        task.setTemplateFileId(templateFileId);
        task.setTemplateRequirement("");
        task.setSelectedModules("[\"teacherProfile\"]");
        task.setProvider("local-mock");
        task.setModelName("mock-model");
        task.setStatus("CONFIRMED");
        task.setDeleted(Boolean.FALSE);
        return task;
    }

    private AiGenerationResult confirmedResult(Long teacherId) {
        AiGenerationResult result = new AiGenerationResult();
        result.setId(200L);
        result.setTaskId(100L);
        result.setTeacherId(teacherId);
        result.setStatus("CONFIRMED");
        result.setConfirmedContent("确认后的本地 mock 内容");
        result.setDeleted(Boolean.FALSE);
        return result;
    }
}
