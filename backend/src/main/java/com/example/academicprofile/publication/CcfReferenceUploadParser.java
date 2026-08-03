package com.example.academicprofile.publication;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class CcfReferenceUploadParser {
    private static final int MAX_CATEGORIES = 100;
    private static final int MAX_VENUES_PER_TYPE = 5_000;
    private static final int MAX_TOTAL_VENUES = 10_000;

    private final ObjectMapper json;

    public CcfReferenceUploadParser(ObjectMapper json) {
        this.json = json;
    }

    public ParsedDataset parse(InputStream input, int expectedYear) {
        try {
            JsonNode root = json.readTree(input);
            if (root == null || !root.isObject()) {
                throw invalid();
            }
            int year = requiredInteger(root, "year");
            if (year != expectedYear) {
                throw new IllegalArgumentException("CCF 数据年度与系统配置不一致");
            }
            String name = requiredText(root, "name", 255);
            String organization = requiredText(root, "organization", 255);
            JsonNode categoryNodes = requiredArray(root, "category", MAX_CATEGORIES);
            JsonNode journalNodes = requiredArray(root, "journals", MAX_VENUES_PER_TYPE);
            JsonNode conferenceNodes = requiredArray(root, "conferences", MAX_VENUES_PER_TYPE);
            if (categoryNodes.isEmpty() || journalNodes.size() + conferenceNodes.size() > MAX_TOTAL_VENUES) {
                throw invalid();
            }

            Map<Integer, Category> categories = new HashMap<>();
            for (JsonNode node : categoryNodes) {
                requireObject(node);
                int id = requiredInteger(node, "id");
                Category previous = categories.put(id, new Category(
                        requiredText(node, "chinese", 255), requiredText(node, "english", 255)));
                if (id < 0 || previous != null) {
                    throw new IllegalArgumentException("CCF 领域编号重复或无效");
                }
            }

            List<ParsedReference> references = new ArrayList<>(journalNodes.size() + conferenceNodes.size());
            parseVenues(journalNodes, VenueType.JOURNAL, categories, references);
            parseVenues(conferenceNodes, VenueType.CONFERENCE, categories, references);
            return new ParsedDataset(year, name, organization, categories.size(), journalNodes.size(),
                    conferenceNodes.size(), List.copyOf(references));
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new IllegalArgumentException("CCF JSON 无法解析");
        }
    }

    private void parseVenues(JsonNode nodes, VenueType type, Map<Integer, Category> categories,
            List<ParsedReference> output) {
        Set<String> classificationKeys = new HashSet<>();
        for (JsonNode node : nodes) {
            requireObject(node);
            int categoryId = requiredInteger(node, "category_id");
            Category category = categories.get(categoryId);
            String rank = requiredText(node, "rank", 1);
            String standardName = requiredText(node, "name", 500);
            String normalizedName = PublicationVenueNormalizer.normalize(standardName);
            if (category == null || !(rank.equals("A") || rank.equals("B") || rank.equals("C"))) {
                throw new IllegalArgumentException("CCF 条目等级或领域无效");
            }
            String classificationKey = categoryId + "\u0000" + normalizedName;
            if (normalizedName.isEmpty() || !classificationKeys.add(classificationKey)) {
                throw new IllegalArgumentException("CCF 条目标准名称重复或冲突");
            }
            String abbreviation = requiredOptionalText(node, "abbr", 128);
            String publisher = requiredOptionalText(node, "publisher", 500);
            String sourceUrl = requiredOptionalText(node, "url", 1024);
            output.add(new ParsedReference(type, categoryId, category.chinese(), category.english(), rank,
                    abbreviation, standardName, normalizedName,
                    normalizedOrNull(abbreviation), optionalText(node, "aliases", 2000), publisher, sourceUrl));
        }
    }

    private JsonNode requiredArray(JsonNode node, String field, int maxItems) {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray() || value.size() > maxItems) {
            throw invalid();
        }
        return value;
    }

    private int requiredInteger(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
            throw invalid();
        }
        return value.intValue();
    }

    private String requiredText(JsonNode node, String field, int maxLength) {
        String value = text(node, field, maxLength, true);
        if (value == null || value.isEmpty()) {
            throw invalid();
        }
        return value;
    }

    private String requiredOptionalText(JsonNode node, String field, int maxLength) {
        if (!node.has(field)) {
            throw invalid();
        }
        return text(node, field, maxLength, false);
    }

    private String optionalText(JsonNode node, String field, int maxLength) {
        return node.has(field) ? text(node, field, maxLength, false) : null;
    }

    private String text(JsonNode node, String field, int maxLength, boolean rejectNull) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            if (rejectNull) throw invalid();
            return null;
        }
        if (!value.isTextual()) throw invalid();
        String trimmed = value.textValue().trim();
        if (trimmed.length() > maxLength) throw new IllegalArgumentException("CCF 文本字段超出长度限制");
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void requireObject(JsonNode node) {
        if (!node.isObject()) throw invalid();
    }

    private String normalizedOrNull(String value) {
        String normalized = PublicationVenueNormalizer.normalize(value);
        return normalized.isEmpty() ? null : normalized;
    }

    private IllegalArgumentException invalid() {
        return new IllegalArgumentException("CCF JSON 结构无效");
    }

    private record Category(String chinese, String english) {}

    public record ParsedDataset(int year, String name, String organization, int categoryCount,
            int journalCount, int conferenceCount, List<ParsedReference> references) {
        public int recordCount() { return journalCount + conferenceCount; }
    }

    public record ParsedReference(VenueType venueType, int categoryId, String categoryName,
            String categoryNameEn, String rank, String abbreviation, String standardName,
            String normalizedName, String normalizedAbbreviation, String aliases, String publisher,
            String sourceUrl) {}
}
