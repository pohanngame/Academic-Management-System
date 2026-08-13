package com.example.academicprofile.word;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
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
    private static final String AUTO_APPENDED_STYLE = "AcademicProfileAutoAppended";

    private static final Pattern TEMPLATE_TAG_PATTERN = Pattern.compile("\\{\\{[^{}]+}}", Pattern.DOTALL);
    private static final int MAX_XML_ENTRY_BYTES = 2 * 1024 * 1024;
    private static final int MAX_XML_TOTAL_BYTES = 8 * 1024 * 1024;

    public WordTemplateInspection inspect(InputStream input) {
        return inspect(readBytes(input));
    }

    public WordTemplateValidationResult validate(InputStream input) {
        return inspectDetailed(readBytes(input), true).validation();
    }

    public byte[] appendPlaceholderAtEnd(InputStream input) {
        byte[] original = readBytes(input);
        DetailedInspection inspection = inspectDetailed(original, true);
        if (inspection.validation().code() != WordTemplateValidationCode.PLACEHOLDER_MISSING) {
            throw new WordTemplateValidationException(
                    WordTemplateValidationCode.MULTIPLE_PLACEHOLDERS,
                    "仅能为未包含正文占位符的模板自动添加插入位置",
                    inspection.validation().validPlaceholderCount());
        }
        byte[] normalized;
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(original));
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.setStyle(AUTO_APPENDED_STYLE);
            paragraph.createRun().setText(PLACEHOLDER);
            document.write(output);
            normalized = output.toByteArray();
        } catch (IOException | RuntimeException ex) {
            throw invalidTemplate();
        }
        inspect(normalized);
        return normalized;
    }

    public byte[] createExampleTemplate() {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XWPFParagraph title = document.createParagraph();
            title.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = title.createRun();
            titleRun.setBold(true);
            titleRun.setFontSize(18);
            titleRun.setText("AI 材料示例模板");
            document.createParagraph().createRun().setText("下方位置将插入已确认的 AI 正文：");
            document.createParagraph().createRun().setText(PLACEHOLDER);
            document.write(output);
            byte[] bytes = output.toByteArray();
            inspect(bytes);
            return bytes;
        } catch (IOException ex) {
            throw invalidTemplate();
        }
    }

    public byte[] renderBuiltIn(
            WordOutputTemplateType type, String confirmedContent, String modelSummary) {
        if (type == null || type == WordOutputTemplateType.CUSTOM) {
            throw new BusinessException("自定义模板必须使用已上传的 DOCX");
        }
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            String titleText = switch (type) {
                case SYSTEM_GENERAL -> "AI 生成材料";
                case TEACHER_ACHIEVEMENT -> "教师成果汇总";
                case SIMPLE_REPORT -> "简洁报告";
                case CUSTOM -> throw new IllegalStateException("unreachable");
            };
            XWPFParagraph title = document.createParagraph();
            title.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = title.createRun();
            titleRun.setBold(true);
            titleRun.setFontSize(type == WordOutputTemplateType.SIMPLE_REPORT ? 16 : 18);
            titleRun.setText(titleText);
            if (StringUtils.hasText(modelSummary)) {
                XWPFParagraph metadata = document.createParagraph();
                XWPFRun metadataRun = metadata.createRun();
                metadataRun.setFontSize(9);
                metadataRun.setColor("666666");
                metadataRun.setText("生成模型：" + modelSummary);
            }
            if (type == WordOutputTemplateType.TEACHER_ACHIEVEMENT) {
                XWPFRun heading = document.createParagraph().createRun();
                heading.setBold(true);
                heading.setText("成果材料");
            }
            appendContent(document, confirmedContent);
            document.write(output);
            byte[] rendered = output.toByteArray();
            verifyBuiltInRendered(rendered, confirmedContent, titleText);
            return rendered;
        } catch (IOException ex) {
            throw new BusinessException("Word 文件生成失败");
        }
    }

    public String structureFor(WordOutputTemplateType type) {
        return switch (type) {
            case SYSTEM_GENERAL -> "系统通用模板：标题、生成模型、正文";
            case TEACHER_ACHIEVEMENT -> "教师成果汇总模板：标题、成果材料、正文";
            case SIMPLE_REPORT -> "简洁报告模板：标题、正文";
            case CUSTOM -> "";
        };
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
            throw invalidTemplate();
        }
        verifyRendered(renderedBytes, inspection, confirmedContent);
        return renderedBytes;
    }

    private WordTemplateInspection inspect(byte[] bytes) {
        return inspectDetailed(bytes, false).inspection();
    }

    private DetailedInspection inspectDetailed(byte[] bytes, boolean allowMissing) {
        rejectEncryptedOrNonDocx(bytes);
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            InspectionBuilder builder = new InspectionBuilder();
            inspectSupportedBody(document.getBodyElements(), builder);
            rejectUnsupportedLocations(document, builder.staticFragments);
            validatePackageSafety(bytes, builder.placeholderCount);
            validatePoiTlTags(bytes, builder.placeholderCount);
            if (builder.placeholderCount > 1) {
                throw new WordTemplateValidationException(
                        WordTemplateValidationCode.MULTIPLE_PLACEHOLDERS,
                        "检测到多个正文插入位置，请只保留一个 {{aiContent}}",
                        builder.placeholderCount);
            }
            String structureText = builder.structure.toString().trim();
            if (structureText.length() > MAX_STRUCTURE_TEXT_LENGTH) {
                throw new WordTemplateValidationException(
                        WordTemplateValidationCode.INVALID_DOCX,
                        "Word 模板结构文字超过 " + MAX_STRUCTURE_TEXT_LENGTH + " 字符，请简化后重试",
                        builder.placeholderCount);
            }
            WordTemplateInspection inspection = new WordTemplateInspection(
                    structureText, List.copyOf(builder.staticFragments));
            if (builder.placeholderCount == 0) {
                WordTemplateValidationResult missing = new WordTemplateValidationResult(
                        WordTemplateValidationCode.PLACEHOLDER_MISSING,
                        "未检测到正文插入位置",
                        0,
                        "未指定",
                        false);
                if (!allowMissing) {
                    throw new WordTemplateValidationException(
                            missing.code(), missing.message(), missing.validPlaceholderCount());
                }
                return new DetailedInspection(inspection, missing);
            }
            String location = builder.autoAppended ? "文档末尾" : builder.insertionLocation;
            return new DetailedInspection(
                    inspection,
                    new WordTemplateValidationResult(
                            WordTemplateValidationCode.VALID_EXACT_PLACEHOLDER,
                            "格式正常，已找到正文插入位置",
                            1,
                            location,
                            builder.autoAppended));
        } catch (WordTemplateValidationException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw invalidTemplate();
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
            throw unsupportedLocation();
        }
        validateOnlySupportedTag(text);
        builder.placeholderCount += placeholderCount;
        if (placeholderCount > 0) {
            builder.insertionLocation = "正文或普通表格单元格";
            if (AUTO_APPENDED_STYLE.equals(paragraph.getStyle())) {
                builder.autoAppended = true;
            }
        }
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
                throw new WordTemplateValidationException(
                        WordTemplateValidationCode.UNSUPPORTED_PLACEHOLDER_LOCATION,
                        "正文插入位置不能放在" + location + "中",
                        0);
            }
            if (element instanceof XWPFTable table) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        for (XWPFParagraph paragraph : cell.getParagraphs()) {
                            if (containsTemplateTag(paragraph.getText())) {
                                throw new WordTemplateValidationException(
                                        WordTemplateValidationCode.UNSUPPORTED_PLACEHOLDER_LOCATION,
                                        "正文插入位置不能放在" + location + "中",
                                        0);
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
                throw new WordTemplateValidationException(
                        WordTemplateValidationCode.UNKNOWN_TEMPLATE_TAG,
                        "模板中存在不支持的占位标签，仅支持 {{aiContent}}",
                        supportedPlaceholderCount);
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw invalidTemplate();
        }
    }

    private void verifyRendered(
            byte[] renderedBytes,
            WordTemplateInspection inspection,
            String confirmedContent) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(renderedBytes))) {
            String outputText = visibleText(document);
            if (outputText.contains(PLACEHOLDER)) {
                throw new BusinessException("Word 文件生成失败：仍存在未替换的正文位置");
            }
            for (String fragment : inspection.staticFragments()) {
                if (!outputText.contains(fragment)) {
                    throw new BusinessException("Word 文件生成失败：模板固定内容不完整");
                }
            }
            for (String line : confirmedContent.split("\\R")) {
                String trimmed = line.trim();
                if (StringUtils.hasText(trimmed) && !outputText.contains(trimmed)) {
                    throw new BusinessException("Word 文件生成失败：正文内容不完整");
                }
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw invalidTemplate();
        }
        try (XWPFTemplate template = XWPFTemplate.compile(new ByteArrayInputStream(renderedBytes))) {
            boolean unresolved = template.getElementTemplates().stream()
                    .anyMatch(metaTemplate -> PLACEHOLDER.equals(metaTemplate.variable().trim()));
            if (unresolved) {
                throw new BusinessException("Word 文件生成失败：仍存在未替换的正文位置");
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw invalidTemplate();
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
                throw new WordTemplateValidationException(
                        WordTemplateValidationCode.UNKNOWN_TEMPLATE_TAG,
                        "模板中存在不支持的占位标签，仅支持 {{aiContent}}",
                        0);
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
            throw invalidTemplate();
        }
    }

    private WordTemplateValidationException invalidTemplate() {
        return new WordTemplateValidationException(
                WordTemplateValidationCode.INVALID_DOCX,
                "文件不是可解析的 DOCX，或文件结构已损坏",
                0);
    }

    private WordTemplateValidationException unsupportedLocation() {
        return new WordTemplateValidationException(
                WordTemplateValidationCode.UNSUPPORTED_PLACEHOLDER_LOCATION,
                "正文插入位置不能放在批注、修订、内容控件、文本框或复杂域中",
                0);
    }

    private void rejectEncryptedOrNonDocx(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            throw invalidTemplate();
        }
        boolean zip = bytes[0] == 'P' && bytes[1] == 'K';
        if (zip) {
            return;
        }
        boolean ole = (bytes[0] & 0xff) == 0xD0
                && (bytes[1] & 0xff) == 0xCF
                && (bytes[2] & 0xff) == 0x11
                && (bytes[3] & 0xff) == 0xE0;
        if (ole) {
            throw new WordTemplateValidationException(
                    WordTemplateValidationCode.ENCRYPTED_DOCX,
                    "不支持加密、需要打开密码或旧版 DOC 文件",
                    0);
        }
        throw invalidTemplate();
    }

    private void validatePackageSafety(byte[] bytes, int supportedPlaceholderCount) {
        boolean contentTypesFound = false;
        boolean documentFound = false;
        int totalXmlBytes = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/').toLowerCase();
                if (name.contains("vbaproject.bin") || name.contains("activex")) {
                    throw new WordTemplateValidationException(
                            WordTemplateValidationCode.UNSUPPORTED_FILE_TYPE,
                            "不支持包含宏或 ActiveX 内容的 Word 模板",
                            supportedPlaceholderCount);
                }
                if (!name.endsWith(".xml") && !name.endsWith(".rels")) {
                    continue;
                }
                byte[] xmlBytes = zip.readNBytes(MAX_XML_ENTRY_BYTES + 1);
                if (xmlBytes.length > MAX_XML_ENTRY_BYTES) {
                    throw invalidTemplate();
                }
                totalXmlBytes += xmlBytes.length;
                if (totalXmlBytes > MAX_XML_TOTAL_BYTES) {
                    throw invalidTemplate();
                }
                String xml = new String(xmlBytes, StandardCharsets.UTF_8);
                if ("[content_types].xml".equals(name)) {
                    contentTypesFound = true;
                    if (xml.toLowerCase().contains("macroenabled")) {
                        throw new WordTemplateValidationException(
                                WordTemplateValidationCode.UNSUPPORTED_FILE_TYPE,
                                "不支持 DOCM 或包含宏的 Word 模板",
                                supportedPlaceholderCount);
                    }
                }
                if (name.endsWith(".rels") && xml.matches("(?s).*TargetMode\\s*=\\s*[\"']External[\"'].*")) {
                    throw new WordTemplateValidationException(
                            WordTemplateValidationCode.INVALID_DOCX,
                            "模板不能包含外部资源或外部链接关系",
                            supportedPlaceholderCount);
                }
                if (!name.startsWith("word/") || !name.endsWith(".xml")) {
                    continue;
                }
                String visibleXmlText = xml.replaceAll("<[^>]+>", "");
                validateOnlySupportedTag(visibleXmlText);
                int packagePlaceholderCount = countExactPlaceholder(visibleXmlText);
                if (countExactPlaceholder(xml) > packagePlaceholderCount) {
                    throw unsupportedLocation();
                }
                if ("word/document.xml".equals(name)) {
                    documentFound = true;
                    if (packagePlaceholderCount != supportedPlaceholderCount) {
                        throw unsupportedLocation();
                    }
                } else if (packagePlaceholderCount > 0) {
                    throw unsupportedLocation();
                }
            }
        } catch (WordTemplateValidationException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw invalidTemplate();
        }
        if (!contentTypesFound || !documentFound) {
            throw invalidTemplate();
        }
    }

    private void appendContent(XWPFDocument document, String content) {
        String safeContent = content == null ? "" : content;
        for (String block : safeContent.split("\\R{2,}", -1)) {
            XWPFParagraph paragraph = document.createParagraph();
            for (String line : block.split("\\R", -1)) {
                XWPFRun run = paragraph.createRun();
                run.setFontSize(11);
                run.setText(line);
                run.addBreak();
            }
        }
    }

    void verifyBuiltInRendered(byte[] bytes, String content, String title) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            String visible = visibleText(document);
            String normalizedVisible = normalizeLayoutWhitespace(visible);
            if (visible.contains(PLACEHOLDER)
                    || !normalizedVisible.contains(normalizeLayoutWhitespace(title))
                    || !containsNonEmptyLinesInOrder(normalizedVisible, content)) {
                throw new BusinessException("系统 Word 模板生成结果无效");
            }
        } catch (IOException ex) {
            throw new BusinessException("系统 Word 模板生成结果无效");
        }
    }

    private boolean containsNonEmptyLinesInOrder(String normalizedVisible, String content) {
        int fromIndex = 0;
        for (String line : (content == null ? "" : content).split("\\R", -1)) {
            String normalizedLine = normalizeLayoutWhitespace(line);
            if (normalizedLine.isEmpty()) {
                continue;
            }
            int foundAt = normalizedVisible.indexOf(normalizedLine, fromIndex);
            if (foundAt < 0) {
                return false;
            }
            fromIndex = foundAt + normalizedLine.length();
        }
        return true;
    }

    private String normalizeLayoutWhitespace(String value) {
        StringBuilder normalized = new StringBuilder();
        boolean pendingSpace = false;
        for (int offset = 0; offset < (value == null ? 0 : value.length());) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                pendingSpace = normalized.length() > 0;
            } else {
                if (pendingSpace) {
                    normalized.append(' ');
                    pendingSpace = false;
                }
                normalized.appendCodePoint(codePoint);
            }
        }
        return normalized.toString();
    }

    public record WordTemplateInspection(String structureText, List<String> staticFragments) {
    }

    private static class InspectionBuilder {
        private final StringBuilder structure = new StringBuilder();
        private final List<String> staticFragments = new ArrayList<>();
        private int placeholderCount;
        private String insertionLocation = "正文或普通表格单元格";
        private boolean autoAppended;
    }

    private record DetailedInspection(
            WordTemplateInspection inspection,
            WordTemplateValidationResult validation) {
    }
}
