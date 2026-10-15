package com.example.academicprofile.timetable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 解析教务教师总表 `#kbtable` 的 HTML。
 *
 * 表结构（2026-09-25 真实页面核对）：
 * 第 1 行是星期，第 2 行是节次组，从第 3 行起每位教师一行，共 43 个 `td`。
 * 第 1 列是 `<nobr>` 中的显示姓名，其余 42 列按位置决定星期与节次组：
 * 每天 6 列，顺序固定为 0102、0304、0506、0708、0910、1112。
 * 没有 kbcontent 子格，也没有教师姓名 class。
 * 单元格原文里的周次范围（如 `(2-13周)`）决定占用哪些周。
 */
@Component
public class KbtableParser {

    /** 每天的节次组，顺序即列顺序；7 天共 42 列。 */
    public static final List<String> SLOT_CODES =
            List.of("0102", "0304", "0506", "0708", "0910", "1112");

    /** 一位教师的整行 td 数：1 列姓名 + 7 天 × 6 节次组。 */
    public static final int COLUMNS_PER_TEACHER_ROW = 43;

    private static final Pattern TABLE_PATTERN =
            Pattern.compile("<table[^>]*id=[\"']?kbtable[\"']?[^>]*>(.*?)</table>",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ROW_PATTERN =
            Pattern.compile("<tr[^>]*>(.*?)</tr>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CELL_PATTERN =
            Pattern.compile("<td[^>]*>(.*?)</td>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]+>");
    /** 第 1 列的教师显示姓名写在 `<nobr>` 里。 */
    private static final Pattern NOBR_PATTERN =
            Pattern.compile("<nobr[^>]*>(.*?)</nobr>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    /** `(2-13周)`、`1-16周`、`(2-13周(单))` 等写法统一取周次数字段。 */
    private static final Pattern WEEK_RANGE_PATTERN =
            Pattern.compile("(\\d{1,2})\\s*-\\s*(\\d{1,2})\\s*周");
    private static final Pattern WEEK_SINGLE_PATTERN = Pattern.compile("(\\d{1,2})\\s*周");
    private static final Pattern WEEK_LIST_PATTERN = Pattern.compile("([0-9,\\s]{1,80})\\s*周");

    /** 表头占前两行：第 1 行星期、第 2 行节次组。 */
    private static final int HEADER_ROWS = 2;

    public List<ParsedTeacherRow> parse(String html) {
        if (!StringUtils.hasText(html)) {
            return List.of();
        }
        Matcher table = TABLE_PATTERN.matcher(html);
        if (!table.find()) {
            return List.of();
        }
        List<ParsedTeacherRow> teachers = new ArrayList<>();
        Matcher rows = ROW_PATTERN.matcher(table.group(1));
        int rowIndex = -1;
        while (rows.find()) {
            rowIndex++;
            if (rowIndex < HEADER_ROWS) {
                continue;
            }
            ParsedTeacherRow parsed = parseRow(rows.group(1));
            if (parsed != null) {
                teachers.add(parsed);
            }
        }
        return teachers;
    }

    private ParsedTeacherRow parseRow(String rowHtml) {
        List<String> cells = new ArrayList<>();
        Matcher cellMatcher = CELL_PATTERN.matcher(rowHtml);
        while (cellMatcher.find()) {
            cells.add(cellMatcher.group(1));
        }
        if (cells.size() != COLUMNS_PER_TEACHER_ROW) {
            // 不是教师数据行（表头、汇总行或结构变化），跳过而不是猜位置。
            return null;
        }
        String teacherName = extractTeacherName(cells.get(0));
        if (!StringUtils.hasText(teacherName)) {
            return null;
        }
        List<ParsedCell> occupancies = new ArrayList<>();
        // 第 1 列是姓名，其后 42 列：列号 1..42 -> 天 = (列-1)/6，节次组 = (列-1)%6。
        for (int column = 1; column < COLUMNS_PER_TEACHER_ROW; column++) {
            String cellText = toPlainText(cells.get(column));
            if (!StringUtils.hasText(cellText)) {
                continue;
            }
            int dayOfWeek = (column - 1) / SLOT_CODES.size() + 1;
            String slotCode = SLOT_CODES.get((column - 1) % SLOT_CODES.size());
            int[] weeks = extractWeekRange(cellText);
            occupancies.add(new ParsedCell(
                    dayOfWeek, slotCode, weeks[0], weeks[1], cellText));
        }
        return new ParsedTeacherRow(teacherName, occupancies);
    }

    /** 第 1 列的 `<nobr>` 是显示姓名；没有 `<nobr>` 时取该列纯文本。 */
    private String extractTeacherName(String nameCellHtml) {
        Matcher nobr = NOBR_PATTERN.matcher(nameCellHtml);
        while (nobr.find()) {
            String candidate = toPlainText(nobr.group(1));
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        return toPlainText(nameCellHtml);
    }

    /** 原文里的周次范围决定占用周；缺失时按 1-30 全周，避免漏判占用。 */
    private int[] extractWeekRange(String cellText) {
        Matcher range = WEEK_RANGE_PATTERN.matcher(cellText);
        if (range.find()) {
            return normalize(Integer.parseInt(range.group(1)), Integer.parseInt(range.group(2)));
        }
        List<Integer> weeks = new ArrayList<>();
        Matcher list = WEEK_LIST_PATTERN.matcher(cellText);
        while (list.find()) {
            for (String piece : list.group(1).split(",")) {
                String trimmed = piece.trim();
                if (trimmed.matches("\\d{1,2}")) {
                    weeks.add(Integer.parseInt(trimmed));
                }
            }
        }
        if (!weeks.isEmpty()) {
            int min = weeks.stream().mapToInt(Integer::intValue).min().orElse(1);
            int max = weeks.stream().mapToInt(Integer::intValue).max().orElse(30);
            return normalize(min, max);
        }
        Matcher single = WEEK_SINGLE_PATTERN.matcher(cellText);
        if (single.find()) {
            int week = Integer.parseInt(single.group(1));
            return normalize(week, week);
        }
        return new int[] {1, 30};
    }

    private int[] normalize(int start, int end) {
        int low = Math.max(1, Math.min(start, end));
        int high = Math.min(30, Math.max(start, end));
        if (high < low) {
            low = 1;
            high = 30;
        }
        return new int[] {low, high};
    }

    /** 去标签、还原实体、压缩空白。单元格按 `<br>` 分行的课程/班级/周次/教室保留为换行。 */
    static String toPlainText(String html) {
        if (html == null) {
            return "";
        }
        String withoutTags = TAG_PATTERN.matcher(html).replaceAll("\n");
        String decoded = withoutTags
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
        String[] lines = decoded.split("\n");
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(trimmed);
            }
        }
        return builder.toString();
    }

    public record ParsedCell(
            int dayOfWeek,
            String slotCode,
            int weekStart,
            int weekEnd,
            String occupancyText) {
    }

    public record ParsedTeacherRow(String teacherName, List<ParsedCell> cells) {
    }

    /** 便于按姓名去重与查找。 */
    public Map<String, Integer> indexByName(List<ParsedTeacherRow> rows) {
        Map<String, Integer> index = new LinkedHashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            index.put(rows.get(i).teacherName(), i);
        }
        return index;
    }
}
