package com.example.academicprofile.word;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.junit.jupiter.api.Test;

import com.example.academicprofile.common.exception.BusinessException;

public class WordTemplateServiceTest {

    private final WordTemplateService service = new WordTemplateService();

    @Test
    void recognizesAndRendersPlaceholderSplitAcrossRunsInBody() throws IOException {
        byte[] template = documentWithSplitPlaceholder(false);

        WordTemplateService.WordTemplateInspection inspection = service.inspect(new ByteArrayInputStream(template));

        assertTrue(inspection.structureText().contains("项目申报个人材料"));
        assertTrue(inspection.structureText().contains("[AI 内容插入位置]"));
        assertTrue(inspection.structureText().contains("代表成果"));
        assertFalse(inspection.structureText().contains(WordTemplateService.PLACEHOLDER));

        byte[] rendered = service.render(new ByteArrayInputStream(template), "本地 mock 确认内容\n第二段内容");

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(rendered))) {
            String text = visibleText(document);
            assertTrue(text.contains("项目申报个人材料"));
            assertTrue(text.contains("代表成果"));
            assertTrue(text.contains("本地 mock 确认内容"));
            assertTrue(text.contains("第二段内容"));
            assertFalse(text.contains(WordTemplateService.PLACEHOLDER));
        }
    }

    @Test
    void recognizesAndRendersPlaceholderSplitAcrossRunsInNormalTableCell() throws IOException {
        byte[] template = documentWithSplitPlaceholder(true);

        WordTemplateService.WordTemplateInspection inspection = service.inspect(new ByteArrayInputStream(template));
        assertEquals(1, inspection.staticFragments().stream().filter("个人简介："::equals).count());

        byte[] rendered = service.render(new ByteArrayInputStream(template), "表格中的确认内容");

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(rendered))) {
            String text = visibleText(document);
            assertTrue(text.contains("个人简介："));
            assertTrue(text.contains("表格中的确认内容"));
            assertFalse(text.contains(WordTemplateService.PLACEHOLDER));
        }
    }

    @Test
    void rejectsMissingAndDuplicatePlaceholders() throws IOException {
        byte[] missing = documentWithParagraph("仅有静态文字");
        byte[] duplicate;
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(WordTemplateService.PLACEHOLDER);
            document.createParagraph().createRun().setText(WordTemplateService.PLACEHOLDER);
            document.write(output);
            duplicate = output.toByteArray();
        }

        assertThrows(BusinessException.class, () -> service.inspect(new ByteArrayInputStream(missing)));
        assertThrows(BusinessException.class, () -> service.inspect(new ByteArrayInputStream(duplicate)));
    }

    @Test
    void rejectsStructureTextOverLimit() throws IOException {
        String longText = "A".repeat(WordTemplateService.MAX_STRUCTURE_TEXT_LENGTH + 1);
        byte[] template;
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(longText);
            document.createParagraph().createRun().setText(WordTemplateService.PLACEHOLDER);
            document.write(output);
            template = output.toByteArray();
        }

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.inspect(new ByteArrayInputStream(template)));
        assertTrue(exception.getMessage().contains(Integer.toString(WordTemplateService.MAX_STRUCTURE_TEXT_LENGTH)));
    }

    @Test
    void rejectsPlaceholderInHeaderEvenWhenBodyPlaceholderIsValid() throws IOException {
        byte[] template;
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(WordTemplateService.PLACEHOLDER);
            document.createHeader(HeaderFooterType.DEFAULT)
                    .createParagraph()
                    .createRun()
                    .setText(WordTemplateService.PLACEHOLDER);
            document.write(output);
            template = output.toByteArray();
        }

        assertThrows(BusinessException.class, () -> service.inspect(new ByteArrayInputStream(template)));
    }

    @Test
    void reportsMissingPlaceholderAndAppendsOnlyWhenExplicitlyRequested() throws IOException {
        byte[] original = documentWithParagraph("普通用户模板正文");

        WordTemplateValidationResult missing = service.validate(new ByteArrayInputStream(original));

        assertEquals(WordTemplateValidationCode.PLACEHOLDER_MISSING, missing.code());
        assertEquals(0, missing.validPlaceholderCount());
        assertEquals("未指定", missing.insertionLocation());
        assertFalse(missing.autoAppended());

        byte[] normalized = service.appendPlaceholderAtEnd(new ByteArrayInputStream(original));
        WordTemplateValidationResult exact = service.validate(new ByteArrayInputStream(normalized));

        assertEquals(WordTemplateValidationCode.VALID_EXACT_PLACEHOLDER, exact.code());
        assertEquals(1, exact.validPlaceholderCount());
        assertEquals("文档末尾", exact.insertionLocation());
        assertTrue(exact.autoAppended());
        assertTrue(service.inspect(new ByteArrayInputStream(normalized)).structureText()
                .contains("AI 内容插入位置"));
    }

    @Test
    void returnsStableCodesForDuplicateUnknownAndUnsupportedLocations() throws IOException {
        byte[] duplicate;
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(WordTemplateService.PLACEHOLDER);
            document.createParagraph().createRun().setText(WordTemplateService.PLACEHOLDER);
            document.write(output);
            duplicate = output.toByteArray();
        }
        byte[] unknown = documentWithParagraph("{{teacherName}}");
        byte[] header;
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(WordTemplateService.PLACEHOLDER);
            document.createHeader(HeaderFooterType.DEFAULT).createParagraph().createRun()
                    .setText(WordTemplateService.PLACEHOLDER);
            document.write(output);
            header = output.toByteArray();
        }

        assertEquals(
                WordTemplateValidationCode.MULTIPLE_PLACEHOLDERS,
                assertThrows(WordTemplateValidationException.class,
                        () -> service.validate(new ByteArrayInputStream(duplicate))).code());
        assertEquals(
                WordTemplateValidationCode.UNKNOWN_TEMPLATE_TAG,
                assertThrows(WordTemplateValidationException.class,
                        () -> service.validate(new ByteArrayInputStream(unknown))).code());
        assertEquals(
                WordTemplateValidationCode.UNSUPPORTED_PLACEHOLDER_LOCATION,
                assertThrows(WordTemplateValidationException.class,
                        () -> service.validate(new ByteArrayInputStream(header))).code());
    }

    @Test
    void exampleAndAllBuiltInTemplatesRenderToReopenableDocx() throws IOException {
        byte[] example = service.createExampleTemplate();
        assertEquals(
                WordTemplateValidationCode.VALID_EXACT_PLACEHOLDER,
                service.validate(new ByteArrayInputStream(example)).code());

        for (WordOutputTemplateType type : List.of(
                WordOutputTemplateType.SYSTEM_GENERAL,
                WordOutputTemplateType.TEACHER_ACHIEVEMENT,
                WordOutputTemplateType.SIMPLE_REPORT)) {
            byte[] rendered = service.renderBuiltIn(type, "固定测试正文", "测试服务 / 测试模型");
            try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(rendered))) {
                String text = visibleText(document);
                assertTrue(text.contains("固定测试正文"));
                assertFalse(text.contains(WordTemplateService.PLACEHOLDER));
            }
        }
    }

    @Test
    void allBuiltInTemplatesAcceptRealMultilineContent() throws IOException {
        String content = "  第一段：中文说明（含括号）。\r\n"
                + "Second paragraph: English text.\r\n\r\n"
                + "1. 第一项\n2. 第二项  ";

        for (WordOutputTemplateType type : List.of(
                WordOutputTemplateType.SYSTEM_GENERAL,
                WordOutputTemplateType.TEACHER_ACHIEVEMENT,
                WordOutputTemplateType.SIMPLE_REPORT)) {
            byte[] rendered = service.renderBuiltIn(type, content, "测试服务 / 测试模型");
            try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(rendered))) {
                String text = visibleText(document);
                assertTrue(text.contains("第一段：中文说明（含括号）。"));
                assertTrue(text.contains("Second paragraph: English text."));
                assertTrue(text.indexOf("1. 第一项") < text.indexOf("2. 第二项"));
                assertFalse(text.contains(WordTemplateService.PLACEHOLDER));
            }
        }
    }

    @Test
    void builtInVerificationRejectsMissingReorderedTitleAndPlaceholderContent() throws IOException {
        assertThrows(BusinessException.class, () -> service.verifyBuiltInRendered(
                visibleDocx("标题", "第一行"), "第一行\n第二行", "标题"));
        assertThrows(BusinessException.class, () -> service.verifyBuiltInRendered(
                visibleDocx("标题", "第二行", "第一行"), "第一行\n第二行", "标题"));
        assertThrows(BusinessException.class, () -> service.verifyBuiltInRendered(
                visibleDocx("第一行", "第二行"), "第一行\n第二行", "标题"));
        assertThrows(BusinessException.class, () -> service.verifyBuiltInRendered(
                visibleDocx("标题", "第一行", WordTemplateService.PLACEHOLDER), "第一行", "标题"));
    }

    private byte[] visibleDocx(String... paragraphs) throws IOException {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (String paragraph : paragraphs) {
                document.createParagraph().createRun().setText(paragraph);
            }
            document.write(output);
            return output.toByteArray();
        }
    }

    @Test
    void rejectsCorruptOleAndExternalRelationshipDocumentsWithSafeCodes() throws IOException {
        byte[] oleHeader = new byte[] {
                (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, 0, 0, 0, 0
        };
        WordTemplateValidationException corrupt = assertThrows(
                WordTemplateValidationException.class,
                () -> service.validate(new ByteArrayInputStream("not-a-docx".getBytes())));
        WordTemplateValidationException encrypted = assertThrows(
                WordTemplateValidationException.class,
                () -> service.validate(new ByteArrayInputStream(oleHeader)));

        byte[] external;
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(WordTemplateService.PLACEHOLDER);
            document.createParagraph().createHyperlinkRun("https://invalid.example")
                    .setText("外部链接");
            document.write(output);
            external = output.toByteArray();
        }
        WordTemplateValidationException externalFailure = assertThrows(
                WordTemplateValidationException.class,
                () -> service.validate(new ByteArrayInputStream(external)));

        assertEquals(WordTemplateValidationCode.INVALID_DOCX, corrupt.code());
        assertEquals(WordTemplateValidationCode.ENCRYPTED_DOCX, encrypted.code());
        assertEquals(WordTemplateValidationCode.INVALID_DOCX, externalFailure.code());
        assertFalse(externalFailure.getMessage().contains("invalid.example"));
    }

    @Test
    void rejectsPlaceholderInFooterWithStableLocationCode() throws IOException {
        byte[] template;
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(WordTemplateService.PLACEHOLDER);
            document.createFooter(HeaderFooterType.DEFAULT).createParagraph().createRun()
                    .setText(WordTemplateService.PLACEHOLDER);
            document.write(output);
            template = output.toByteArray();
        }

        assertEquals(
                WordTemplateValidationCode.UNSUPPORTED_PLACEHOLDER_LOCATION,
                assertThrows(WordTemplateValidationException.class,
                        () -> service.validate(new ByteArrayInputStream(template))).code());
    }

    @Test
    void rejectsPlaceholdersInCommentsContentControlsRevisionsAndComplexFields() throws IOException {
        byte[] base = documentWithSplitPlaceholder(false);
        byte[] comment = addZipEntry(
                base,
                "word/comments.xml",
                "<w:comments xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                        + "<w:comment w:id=\"1\"><w:p><w:r><w:t>{{aiContent}}</w:t></w:r></w:p>"
                        + "</w:comment></w:comments>");
        byte[] contentControl = replaceDocumentXml(base, xml -> xml.replace(
                "</w:body>",
                "<w:sdt><w:sdtContent><w:p><w:r><w:t>{{aiContent}}</w:t></w:r></w:p>"
                        + "</w:sdtContent></w:sdt></w:body>"));
        byte[] revision = replaceDocumentXml(base, xml -> xml.replace(
                "</w:body>",
                "<w:p><w:ins w:id=\"1\"><w:r><w:t>{{aiContent}}</w:t></w:r></w:ins></w:p></w:body>"));
        byte[] field = replaceDocumentXml(base, xml -> xml.replace(
                "</w:body>",
                "<w:p><w:r><w:fldChar w:fldCharType=\"begin\"/></w:r>"
                        + "<w:r><w:instrText>{{aiContent}}</w:instrText></w:r>"
                        + "<w:r><w:fldChar w:fldCharType=\"end\"/></w:r></w:p></w:body>"));

        List<byte[]> unsupportedDocuments = List.of(comment, contentControl, revision, field);
        List<String> labels = List.of("comment", "content-control", "revision", "complex-field");
        for (int index = 0; index < unsupportedDocuments.size(); index++) {
            byte[] unsupported = unsupportedDocuments.get(index);
            String label = labels.get(index);
            assertEquals(
                    WordTemplateValidationCode.UNSUPPORTED_PLACEHOLDER_LOCATION,
                    assertThrows(WordTemplateValidationException.class,
                            () -> service.validate(new ByteArrayInputStream(unsupported)), label).code(),
                    label);
        }
    }

    public static byte[] documentWithSplitPlaceholder(boolean inTable) throws IOException {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("项目申报个人材料");
            if (inTable) {
                XWPFTable table = document.createTable(1, 1);
                XWPFTableCell cell = table.getRow(0).getCell(0);
                XWPFParagraph paragraph = cell.getParagraphs().get(0);
                paragraph.createRun().setText("个人简介：");
                paragraph.createRun().setText("{{ai");
                paragraph.createRun().setText("Content}}");
            } else {
                XWPFParagraph paragraph = document.createParagraph();
                paragraph.createRun().setText("正文：");
                paragraph.createRun().setText("{{ai");
                paragraph.createRun().setText("Content}}");
            }
            XWPFTable table = document.createTable(1, 2);
            XWPFTableRow row = table.getRow(0);
            row.getCell(0).setText("栏目");
            row.getCell(1).setText("代表成果");
            document.write(output);
            return output.toByteArray();
        }
    }

    private byte[] documentWithParagraph(String text) throws IOException {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(text);
            document.write(output);
            return output.toByteArray();
        }
    }

    private String visibleText(XWPFDocument document) {
        StringBuilder text = new StringBuilder();
        document.getParagraphs().forEach(paragraph -> text.append(paragraph.getText()).append('\n'));
        for (XWPFTable table : document.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    text.append(cell.getText()).append('\n');
                }
            }
        }
        return text.toString();
    }

    private byte[] replaceDocumentXml(byte[] docx, UnaryOperator<String> transform) throws IOException {
        return rewriteZip(docx, "word/document.xml", transform, null, null);
    }

    private byte[] addZipEntry(byte[] docx, String name, String content) throws IOException {
        return rewriteZip(docx, null, UnaryOperator.identity(), name, content);
    }

    private byte[] rewriteZip(
            byte[] docx,
            String targetEntry,
            UnaryOperator<String> transform,
            String addedEntry,
            String addedContent) throws IOException {
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(docx));
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                ZipOutputStream zip = new ZipOutputStream(output)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                zip.putNextEntry(new ZipEntry(entry.getName()));
                byte[] content = input.readAllBytes();
                if (entry.getName().equals(targetEntry)) {
                    content = transform.apply(new String(content, java.nio.charset.StandardCharsets.UTF_8))
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                }
                zip.write(content);
                zip.closeEntry();
            }
            if (addedEntry != null) {
                zip.putNextEntry(new ZipEntry(addedEntry));
                zip.write(addedContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            zip.finish();
            return output.toByteArray();
        }
    }
}
