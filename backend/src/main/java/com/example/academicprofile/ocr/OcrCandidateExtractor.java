package com.example.academicprofile.ocr;

import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class OcrCandidateExtractor {

    private static final Pattern YEAR_PATTERN = Pattern.compile("(19|20)\\d{2}");
    private static final Pattern DOI_PATTERN = Pattern.compile("(?i)\\b10\\.\\d{4,9}/[-._;()/:A-Z0-9]+");
    private static final Pattern PATENT_NUMBER_PATTERN = Pattern.compile("(?i)\\b(CN|US|EP|WO)\\s?[A-Z0-9.\\-]{5,}");
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{4})[-/.年](\\d{1,2})[-/.月](\\d{1,2})");

    public OcrResult extract(Long taskId, Long teacherId, OcrTargetType targetType, String rawText) {
        OcrResult result = new OcrResult();
        result.setTaskId(taskId);
        result.setTeacherId(teacherId);
        result.setTargetType(targetType.name());
        result.setStatus("DRAFT");
        result.setConfidence(null);
        switch (targetType) {
            case PAPER -> applyPaper(result, rawText);
            case PATENT -> applyPatent(result, rawText);
            case CERTIFICATE -> applyCertificate(result, rawText);
        }
        result.setResultJson("{}");
        return result;
    }

    private void applyPaper(OcrResult result, String rawText) {
        result.setTitle(limit(firstMeaningfulLine(rawText), 500));
        result.setPublishYear(firstYear(rawText));
        result.setDoi(limit(firstMatch(DOI_PATTERN, rawText), 255));
        result.setAuthors(limit(lineAfterAny(rawText, "author", "authors", "作者"), 5000));
        result.setPublicationName(limit(lineAfterAny(rawText, "journal", "conference", "期刊", "会议"), 255));
        result.setKeywords(limit(lineAfterAny(rawText, "keywords", "关键词", "关键字"), 1000));
        result.setAbstractText(limit(sectionAfterAny(rawText, "abstract", "摘要"), 20000));
        result.setPublicationType("OCR");
    }

    private void applyPatent(OcrResult result, String rawText) {
        String patentName = lineAfterAny(rawText, "patent name", "title", "专利名称", "名称");
        result.setPatentName(limit(firstText(patentName, firstMeaningfulLine(rawText)), 255));
        result.setPatentNumber(limit(firstText(firstMatch(PATENT_NUMBER_PATTERN, rawText),
                lineAfterAny(rawText, "patent number", "application number", "专利号", "申请号")), 128));
        result.setPatentType(limit(lineAfterAny(rawText, "type", "专利类型", "类型"), 128));
        result.setInventors(limit(lineAfterAny(rawText, "inventor", "inventors", "发明人"), 5000));
        result.setApplicationDate(firstDate(lineAfterAny(rawText, "application date", "申请日", "申请日期")));
        result.setAuthorizationDate(firstDate(lineAfterAny(rawText, "authorization date", "授权日", "授权公告日")));
        result.setDescription(limit(rawText, 5000));
    }

    private void applyCertificate(OcrResult result, String rawText) {
        String certificateName = lineAfterAny(rawText, "certificate", "certification", "证书名称", "证书");
        result.setCertificateName(limit(firstText(certificateName, firstMeaningfulLine(rawText)), 255));
        result.setCertificateType(limit(lineAfterAny(rawText, "type", "证书类型", "类型"), 128));
        result.setIssuingAuthority(limit(lineAfterAny(rawText, "authority", "issuer", "issuing authority", "颁发机构", "发证机关"), 255));
        result.setIssueDate(firstDate(lineAfterAny(rawText, "issue date", "颁发日期", "发证日期")));
        result.setDescription(limit(rawText, 5000));
    }

    private String firstMeaningfulLine(String rawText) {
        if (!StringUtils.hasText(rawText)) {
            return null;
        }
        for (String line : rawText.split("\\R")) {
            String cleaned = line.trim();
            if (cleaned.length() >= 4 && !cleaned.matches("^[\\p{Punct}\\s]+$")) {
                return cleaned;
            }
        }
        return rawText.trim();
    }

    private Integer firstYear(String rawText) {
        String value = firstMatch(YEAR_PATTERN, rawText);
        return StringUtils.hasText(value) ? Integer.valueOf(value) : null;
    }

    private LocalDate firstDate(String rawText) {
        if (!StringUtils.hasText(rawText)) {
            return null;
        }
        Matcher matcher = DATE_PATTERN.matcher(rawText);
        if (!matcher.find()) {
            return null;
        }
        int year = Integer.parseInt(matcher.group(1));
        int month = Integer.parseInt(matcher.group(2));
        int day = Integer.parseInt(matcher.group(3));
        try {
            return LocalDate.of(year, month, day);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String lineAfterAny(String rawText, String... labels) {
        if (!StringUtils.hasText(rawText)) {
            return null;
        }
        for (String line : rawText.split("\\R")) {
            String trimmed = line.trim();
            String lower = trimmed.toLowerCase(Locale.ROOT);
            for (String label : labels) {
                String normalizedLabel = label.toLowerCase(Locale.ROOT);
                if (lower.startsWith(normalizedLabel)) {
                    String value = trimmed.substring(Math.min(trimmed.length(), label.length()))
                            .replaceFirst("^\\s*[:：-]\\s*", "")
                            .trim();
                    return StringUtils.hasText(value) ? value : null;
                }
            }
        }
        return null;
    }

    private String sectionAfterAny(String rawText, String... labels) {
        String line = lineAfterAny(rawText, labels);
        if (StringUtils.hasText(line)) {
            return line;
        }
        return null;
    }

    private String firstMatch(Pattern pattern, String rawText) {
        if (!StringUtils.hasText(rawText)) {
            return null;
        }
        Matcher matcher = pattern.matcher(rawText);
        return matcher.find() ? matcher.group() : null;
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private String limit(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
