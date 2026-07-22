package com.example.academicprofile.word;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

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
}
