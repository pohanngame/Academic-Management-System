package com.example.academicprofile.word;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFComment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.deepoove.poi.XWPFTemplate;
import com.deepoove.poi.data.TextRenderData;
import com.example.academicprofile.common.exception.BusinessException;

@Service
public class WordTemplateService {

    public static final String PLACEHOLDER = "{{aiContent}}";
    public static final int MAX_STRUCTURE_TEXT_LENGTH = 20_000;

    private static final Pattern TEMPLATE_TAG_PATTERN = Pattern.compile("\\{\\{[^{}]+}}", Pattern.DOTALL);

    public WordTemplateInspection inspect(InputStream input) {
        return inspect(readBytes(input));
    }

    public byte[] render(InputStream input, String confirmedContent) {
        byte[] templateBytes = readBytes(input);
        WordTemplateInspection inspection = inspect(templateBytes);
        byte[] renderedBytes;
        try (XWPFTemplate template = XWPFTemplate.compile(new ByteArrayInputStream(templateBytes));
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            template.render(Map.of("aiContent", new TextRenderData(confirmedContent)));
            template.write(output);
            renderedBytes = output.toByteArray();
        } catch (IOException | RuntimeException ex) {
            throw invalidTemplate("Failed to render Word template", ex);
        }
        verifyRendered(renderedBytes, inspection, confirmedContent);
        return renderedBytes;
    }

    private WordTemplateInspection inspect(byte[] bytes) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            InspectionBuilder builder = new InspectionBuilder();
            inspectSupportedBody(document.getBodyElements(), builder);
            rejectUnsupportedLocations(document, builder.staticFragments);
            validatePoiTlTags(bytes, builder.placeholderCount);
            if (builder.placeholderCount != 1) {
                throw new BusinessException("Word template must contain exactly one {{aiContent}} placeholder in the body or a normal table cell");
            }
            String structureText = builder.structure.toString().trim();
            if (structureText.length() > MAX_STRUCTURE_TEXT_LENGTH) {
                throw new BusinessException(
                        "Word template structure text exceeds the " + MAX_STRUCTURE_TEXT_LENGTH + " character limit");
            }
            return new WordTemplateInspection(structureText, List.copyOf(builder.staticFragments));
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw invalidTemplate("Invalid DOCX Word template", ex);
        }
    }

    private void inspectSupportedBody(List<IBodyElement> bodyElements, InspectionBuilder builder) {
        int paragraphIndex = 0;
        int tableIndex = 0;
        for (IBodyElement element : bodyElements) {
            if (element instanceof XWPFParagraph paragraph) {
                paragraphIndex++;
                inspectParagraph(paragraph, "段落 " + paragraphIndex, builder);
            } else if (element instanceof XWPFTable table) {
                tableIndex++;
                inspectTable(table, tableIndex, builder);
            }
        }
    }

    private void inspectParagraph(XWPFParagraph paragraph, String label, InspectionBuilder builder) {
        String text = paragraph.getText();
        int placeholderCount = countExactPlaceholder(text);
        if (placeholderCount > 0 && hasUnsupportedParagraphContent(paragraph)) {
            throw new BusinessException(
                    "{{aiContent}} is not supported inside comments, revisions, content controls, or complex fields");
        }
        validateOnlySupportedTag(text);
        builder.placeholderCount += placeholderCount;
        addStaticFragments(text, builder.staticFragments);
        String structureLine = structureValue(text);
        if (StringUtils.hasText(structureLine)) {
            appendStructureLine(builder.structure, label + "：" + structureLine);
        }
    }

    private void inspectTable(XWPFTable table, int tableIndex, InspectionBuilder builder) {
        appendStructureLine(builder.structure, "表格 " + tableIndex + "：");
        int rowIndex = 0;
        for (XWPFTableRow row : table.getRows()) {
            rowIndex++;
            List<String> cellValues = new ArrayList<>();
            int cellIndex = 0;
            for (XWPFTableCell cell : row.getTableCells()) {
                cellIndex++;
                StringBuilder cellStructure = new StringBuilder();
                for (XWPFParagraph paragraph : cell.getParagraphs()) {
                    InspectionBuilder cellBuilder = new InspectionBuilder();
                    inspectParagraph(paragraph, "", cellBuilder);
                    builder.placeholderCount += cellBuilder.placeholderCount;
                    builder.staticFragments.addAll(cellBuilder.staticFragments);
                    if (cellBuilder.structure.length() > 0) {
                        if (cellStructure.length() > 0) {
                            cellStructure.append(" / ");
                        }
                        String value = cellBuilder.structure.toString().trim();
                        cellStructure.append(value.startsWith("：") ? value.substring(1) : value);
                    }
                }
                cellValues.add("单元格 " + cellIndex + "=" + cellStructure);
            }
            appendStructureLine(builder.structure, "  第 " + rowIndex + " 行：" + String.join(" | ", cellValues));
        }
    }

    private void rejectUnsupportedLocations(XWPFDocument document, List<String> staticFragments) {
        for (XWPFHeader header : document.getHeaderList()) {
            rejectTags(header.getBodyElements(), "页眉");
            collectStaticFragments(header.getBodyElements(), staticFragments);
        }
        for (XWPFFooter footer : document.getFooterList()) {
            rejectTags(footer.getBodyElements(), "页脚");
            collectStaticFragments(footer.getBodyElements(), staticFragments);
        }
        XWPFComment[] comments = document.getComments();
        if (comments != null) {
            for (XWPFComment comment : comments) {
                rejectTags(comment.getBodyElements(), "批注");
            }
        }
    }

    private void collectStaticFragments(List<IBodyElement> bodyElements, List<String> staticFragments) {
        for (IBodyElement element : bodyElements) {
            if (element instanceof XWPFParagraph paragraph) {
                addStaticFragments(paragraph.getText(), staticFragments);
            } else if (element instanceof XWPFTable table) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        for (XWPFParagraph paragraph : cell.getParagraphs()) {
                            addStaticFragments(paragraph.getText(), staticFragments);
                        }
                    }
                }
            }
        }
    }

    private void rejectTags(List<IBodyElement> bodyElements, String location) {
        for (IBodyElement element : bodyElements) {
            if (element instanceof XWPFParagraph paragraph && containsTemplateTag(paragraph.getText())) {
                throw new BusinessException("Word template placeholders are not supported in " + location);
            }
            if (element instanceof XWPFTable table) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        for (XWPFParagraph paragraph : cell.getParagraphs()) {
                            if (containsTemplateTag(paragraph.getText())) {
                                throw new BusinessException("Word template placeholders are not supported in " + location);
                            }
                        }
                    }
                }
            }
        }
    }

    private void validatePoiTlTags(byte[] bytes, int supportedPlaceholderCount) {
        try (XWPFTemplate template = XWPFTemplate.compile(new ByteArrayInputStream(bytes))) {
            List<String> variables = template.getElementTemplates().stream()
                    .map(metaTemplate -> metaTemplate.variable().trim())
                    .toList();
            if (variables.size() != supportedPlaceholderCount
                    || variables.stream().anyMatch(variable -> !PLACEHOLDER.equals(variable))) {
                throw new BusinessException(
                        "Word template must contain only one supported {{aiContent}} placeholder");
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw invalidTemplate("Invalid poi-tl Word template", ex);
        }
    }

    private void verifyRendered(
            byte[] renderedBytes,
            WordTemplateInspection inspection,
            String confirmedContent) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(renderedBytes))) {
            String outputText = visibleText(document);
            if (outputText.contains(PLACEHOLDER)) {
                throw new BusinessException("Rendered Word file still contains {{aiContent}}");
            }
            for (String fragment : inspection.staticFragments()) {
                if (!outputText.contains(fragment)) {
                    throw new BusinessException("Rendered Word file lost template static content");
                }
            }
            for (String line : confirmedContent.split("\\R")) {
                String trimmed = line.trim();
                if (StringUtils.hasText(trimmed) && !outputText.contains(trimmed)) {
                    throw new BusinessException("Rendered Word file does not contain confirmed AI content");
                }
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw invalidTemplate("Rendered DOCX cannot be reopened", ex);
        }
        try (XWPFTemplate template = XWPFTemplate.compile(new ByteArrayInputStream(renderedBytes))) {
            boolean unresolved = template.getElementTemplates().stream()
                    .anyMatch(metaTemplate -> PLACEHOLDER.equals(metaTemplate.variable().trim()));
            if (unresolved) {
                throw new BusinessException("Rendered Word file still contains {{aiContent}}");
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw invalidTemplate("Rendered DOCX failed poi-tl verification", ex);
        }
    }

    private String visibleText(XWPFDocument document) {
        StringBuilder text = new StringBuilder();
        appendVisibleText(document.getBodyElements(), text);
        for (XWPFHeader header : document.getHeaderList()) {
            appendVisibleText(header.getBodyElements(), text);
        }
        for (XWPFFooter footer : document.getFooterList()) {
            appendVisibleText(footer.getBodyElements(), text);
        }
        return text.toString();
    }

    private void appendVisibleText(List<IBodyElement> bodyElements, StringBuilder output) {
        for (IBodyElement element : bodyElements) {
            if (element instanceof XWPFParagraph paragraph) {
                appendStructureLine(output, paragraph.getText());
            } else if (element instanceof XWPFTable table) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        for (XWPFParagraph paragraph : cell.getParagraphs()) {
                            appendStructureLine(output, paragraph.getText());
                        }
                    }
                }
            }
        }
    }

    private boolean hasUnsupportedParagraphContent(XWPFParagraph paragraph) {
        if (paragraph.getCTP().sizeOfInsArray() > 0
                || paragraph.getCTP().sizeOfDelArray() > 0
                || paragraph.getCTP().sizeOfMoveFromArray() > 0
                || paragraph.getCTP().sizeOfMoveToArray() > 0
                || paragraph.getCTP().sizeOfSdtArray() > 0
                || paragraph.getCTP().sizeOfCustomXmlArray() > 0
                || paragraph.getCTP().sizeOfFldSimpleArray() > 0) {
            return true;
        }
        for (XWPFRun run : paragraph.getRuns()) {
            if (run.getCTR().sizeOfFldCharArray() > 0 || run.getCTR().sizeOfInstrTextArray() > 0) {
                return true;
            }
        }
        return false;
    }

    private void validateOnlySupportedTag(String text) {
        Matcher matcher = TEMPLATE_TAG_PATTERN.matcher(text == null ? "" : text);
        while (matcher.find()) {
            if (!PLACEHOLDER.equals(matcher.group())) {
                throw new BusinessException("Only the {{aiContent}} Word template placeholder is supported");
            }
        }
    }

    private boolean containsTemplateTag(String text) {
        return TEMPLATE_TAG_PATTERN.matcher(text == null ? "" : text).find();
    }

    private int countExactPlaceholder(String text) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(PLACEHOLDER, index)) >= 0) {
            count++;
            index += PLACEHOLDER.length();
        }
        return count;
    }

    private String structureValue(String text) {
        if (text == null) {
            return "";
        }
        return text.replace(PLACEHOLDER, "[AI 内容插入位置]").trim();
    }

    private void addStaticFragments(String text, List<String> fragments) {
        if (text == null) {
            return;
        }
        for (String part : text.split(Pattern.quote(PLACEHOLDER), -1)) {
            String trimmed = part.trim();
            if (StringUtils.hasText(trimmed)) {
                fragments.add(trimmed);
            }
        }
    }

    private void appendStructureLine(StringBuilder builder, String line) {
        if (!StringUtils.hasText(line)) {
            return;
        }
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(line.trim());
    }

    private byte[] readBytes(InputStream input) {
        try (InputStream source = input) {
            return source.readAllBytes();
        } catch (IOException ex) {
            throw invalidTemplate("Failed to read Word template", ex);
        }
    }

    private BusinessException invalidTemplate(String message, Exception cause) {
        return new BusinessException(message + ": " + safeMessage(cause));
    }

    private String safeMessage(Exception ex) {
        return StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : ex.getClass().getSimpleName();
    }

    public record WordTemplateInspection(String structureText, List<String> staticFragments) {
    }

    private static class InspectionBuilder {
        private final StringBuilder structure = new StringBuilder();
        private final List<String> staticFragments = new ArrayList<>();
        private int placeholderCount;
    }
}
