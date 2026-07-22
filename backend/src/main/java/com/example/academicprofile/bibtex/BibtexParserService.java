package com.example.academicprofile.bibtex;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jbibtex.BibTeXDatabase;
import org.jbibtex.BibTeXEntry;
import org.jbibtex.BibTeXParser;
import org.jbibtex.Key;
import org.jbibtex.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.example.academicprofile.common.exception.BusinessException;

@Service
public class BibtexParserService {

    private static final Key KEY_ABSTRACT = new Key("abstract");
    private static final Key KEY_KEYWORDS = new Key("keywords");

    public List<BibtexParsedEntry> parse(String content) {
        try {
            BibTeXParser parser = new BibTeXParser();
            BibTeXDatabase database = parser.parse(new StringReader(content));
            List<BibtexParsedEntry> entries = new ArrayList<>();
            for (BibTeXEntry entry : database.getEntries().values()) {
                entries.add(toParsedEntry(entry));
            }
            if (entries.isEmpty()) {
                throw new BusinessException("No BibTeX entries found");
            }
            return entries;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            List<BibtexParsedEntry> fallbackEntries = parseRecoverableEntries(content);
            if (!fallbackEntries.isEmpty()) {
                return fallbackEntries;
            }
            throw new BusinessException("Failed to parse BibTeX: " + rootMessage(ex));
        }
    }

    private BibtexParsedEntry toParsedEntry(BibTeXEntry entry) {
        try {
            String title = field(entry, BibTeXEntry.KEY_TITLE);
            String authors = field(entry, BibTeXEntry.KEY_AUTHOR);
            String yearText = field(entry, BibTeXEntry.KEY_YEAR);
            String errorMessage = null;
            if (!StringUtils.hasText(title)) {
                errorMessage = "Missing required field: title";
            }
            return new BibtexParsedEntry(
                    rawEntry(entry),
                    value(entry.getType()),
                    value(entry.getKey()),
                    title,
                    authors,
                    field(entry, BibTeXEntry.KEY_JOURNAL),
                    field(entry, BibTeXEntry.KEY_BOOKTITLE),
                    parseYear(yearText),
                    field(entry, BibTeXEntry.KEY_DOI),
                    field(entry, BibTeXEntry.KEY_VOLUME),
                    field(entry, BibTeXEntry.KEY_NUMBER),
                    field(entry, BibTeXEntry.KEY_PAGES),
                    field(entry, BibTeXEntry.KEY_PUBLISHER),
                    field(entry, BibTeXEntry.KEY_URL),
                    field(entry, KEY_ABSTRACT),
                    field(entry, KEY_KEYWORDS),
                    errorMessage);
        } catch (Exception ex) {
            return new BibtexParsedEntry(
                    rawEntry(entry),
                    value(entry.getType()),
                    value(entry.getKey()),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "Failed to map entry: " + rootMessage(ex));
        }
    }

    private List<BibtexParsedEntry> parseRecoverableEntries(String content) {
        List<BibtexParsedEntry> entries = new ArrayList<>();
        for (String segment : entrySegments(content)) {
            try {
                BibTeXParser parser = new BibTeXParser();
                BibTeXDatabase database = parser.parse(new StringReader(segment));
                if (database.getEntries().isEmpty()) {
                    entries.add(failedSegment(segment, "No BibTeX entry found in segment"));
                    continue;
                }
                for (BibTeXEntry entry : database.getEntries().values()) {
                    entries.add(toParsedEntry(entry));
                }
            } catch (Exception ex) {
                entries.add(failedSegment(segment, "Failed to parse entry: " + rootMessage(ex)));
            }
        }
        return entries;
    }

    private List<String> entrySegments(String content) {
        List<String> segments = new ArrayList<>();
        int index = 0;
        while (index < content.length()) {
            int atIndex = content.indexOf('@', index);
            if (atIndex < 0) {
                break;
            }
            int braceIndex = content.indexOf('{', atIndex);
            int parenIndex = content.indexOf('(', atIndex);
            int openIndex = firstPositive(braceIndex, parenIndex);
            if (openIndex < 0) {
                segments.add(content.substring(atIndex).trim());
                break;
            }
            char open = content.charAt(openIndex);
            char close = open == '{' ? '}' : ')';
            int depth = 0;
            boolean completed = false;
            for (int i = openIndex; i < content.length(); i++) {
                char current = content.charAt(i);
                if (current == open) {
                    depth++;
                } else if (current == close) {
                    depth--;
                    if (depth == 0) {
                        segments.add(content.substring(atIndex, i + 1).trim());
                        index = i + 1;
                        completed = true;
                        break;
                    }
                }
            }
            if (!completed) {
                segments.add(content.substring(atIndex).trim());
                break;
            }
        }
        return segments;
    }

    private int firstPositive(int first, int second) {
        if (first < 0) {
            return second;
        }
        if (second < 0) {
            return first;
        }
        return Math.min(first, second);
    }

    private BibtexParsedEntry failedSegment(String segment, String message) {
        return new BibtexParsedEntry(
                limit(segment, 5000),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                message);
    }

    private String field(BibTeXEntry entry, Key key) {
        Value value = entry.getField(key);
        if (value == null) {
            return null;
        }
        return blankToNull(value.toUserString());
    }

    private Integer parseYear(String yearText) {
        if (!StringUtils.hasText(yearText)) {
            return null;
        }
        String digits = yearText.replaceAll("[^0-9]", "");
        if (digits.length() < 4) {
            return null;
        }
        return Integer.parseInt(digits.substring(0, 4));
    }

    private String rawEntry(BibTeXEntry entry) {
        StringBuilder builder = new StringBuilder();
        builder.append("@").append(value(entry.getType())).append("{").append(value(entry.getKey()));
        for (Map.Entry<Key, Value> field : entry.getFields().entrySet()) {
            builder.append(",\n  ")
                    .append(field.getKey().getValue())
                    .append(" = {")
                    .append(field.getValue().toUserString())
                    .append("}");
        }
        builder.append("\n}");
        return limit(builder.toString(), 5000);
    }

    private String value(Key key) {
        return key == null ? null : key.getValue();
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? limit(value.trim(), 20000) : null;
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String rootMessage(Exception ex) {
        Throwable current = ex;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
