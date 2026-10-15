package com.example.academicprofile.timetable;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.academicprofile.timetable.KbtableParser.ParsedCell;
import com.example.academicprofile.timetable.KbtableParser.ParsedTeacherRow;

/**
 * 按真实 `#kbtable` 结构测试：第 1 行星期、第 2 行节次组、从第 3 行起每位教师 43 列。
 * 第 1 列 `<nobr>` 是姓名，其余列由位置决定星期与节次组。
 */
class KbtableParserTest {

    private final KbtableParser parser = new KbtableParser();

    /** 第 1 行星期（43 列）、第 2 行节次组（43 列）。 */
    private static String headerRows() {
        return "<tr><td></td>"
                + "<td>星期一</td><td>星期一</td><td>星期一</td><td>星期一</td><td>星期一</td><td>星期一</td>"
                + "<td>星期二</td><td>星期二</td><td>星期二</td><td>星期二</td><td>星期二</td><td>星期二</td>"
                + "<td>星期三</td><td>星期三</td><td>星期三</td><td>星期三</td><td>星期三</td><td>星期三</td>"
                + "<td>星期四</td><td>星期四</td><td>星期四</td><td>星期四</td><td>星期四</td><td>星期四</td>"
                + "<td>星期五</td><td>星期五</td><td>星期五</td><td>星期五</td><td>星期五</td><td>星期五</td>"
                + "<td>星期六</td><td>星期六</td><td>星期六</td><td>星期六</td><td>星期六</td><td>星期六</td>"
                + "<td>星期日</td><td>星期日</td><td>星期日</td><td>星期日</td><td>星期日</td><td>星期日</td></tr>"
                + "<tr><td></td>"
                + "<td>0102</td><td>0304</td><td>0506</td><td>0708</td><td>0910</td><td>1112</td>".repeat(7)
                + "</tr>";
    }

    /** 把 42 个课格拼成一行；occupied 的键是列号（1..42），值为单元格内 HTML。 */
    private static String teacherRow(String name, java.util.Map<Integer, String> occupied) {
        StringBuilder row = new StringBuilder("<tr><td><nobr>").append(name).append("</nobr></td>");
        for (int column = 1; column <= 42; column++) {
            String content = occupied.getOrDefault(column, "&nbsp;");
            row.append("<td>").append(content).append("</td>");
        }
        return row.append("</tr>").toString();
    }

    private static String table(String rows) {
        return "<html><body><table id=\"kbtable\">" + rows + "</table></body></html>";
    }

    @Test
    void parsesTeacherNameDaySlotAndWeeksFrom43ColumnRow() {
        // 第 1 天第 1 节次组 -> 列 1；第 3 天第 1 节次组 -> 列 13；第 7 天第 6 节次组 -> 列 42。
        String html = table(headerRows() + teacherRow("张三", java.util.Map.of(
                1, "高等数学<br>计算机2301<br>(2-13周)<br>教一101",
                13, "数据结构<br>计算机2302<br>1-16周<br>教二203",
                42, "体育<br>计算机2301<br>(1,3,5周)<br>体育馆")));

        List<ParsedTeacherRow> rows = parser.parse(html);

        assertThat(rows).hasSize(1);
        ParsedTeacherRow teacher = rows.get(0);
        assertThat(teacher.teacherName()).isEqualTo("张三");
        assertThat(teacher.cells()).hasSize(3);

        ParsedCell monday = teacher.cells().get(0);
        assertThat(monday.dayOfWeek()).isEqualTo(1);
        assertThat(monday.slotCode()).isEqualTo("0102");
        assertThat(monday.weekStart()).isEqualTo(2);
        assertThat(monday.weekEnd()).isEqualTo(13);
        assertThat(monday.occupancyText()).contains("高等数学").contains("计算机2301").contains("教一101");

        ParsedCell wednesday = teacher.cells().get(1);
        assertThat(wednesday.dayOfWeek()).isEqualTo(3);
        assertThat(wednesday.slotCode()).isEqualTo("0102");
        assertThat(wednesday.weekStart()).isEqualTo(1);
        assertThat(wednesday.weekEnd()).isEqualTo(16);

        // 第 42 列 = 第 7 天第 6 节次组，且逗号周次列表归一化为 1-5。
        ParsedCell sunday = teacher.cells().get(2);
        assertThat(sunday.dayOfWeek()).isEqualTo(7);
        assertThat(sunday.slotCode()).isEqualTo("1112");
        assertThat(sunday.weekStart()).isEqualTo(1);
        assertThat(sunday.weekEnd()).isEqualTo(5);
    }

    @Test
    void columnPositionMapsEveryDayAndSlot() {
        java.util.Map<Integer, String> occupied = new java.util.LinkedHashMap<>();
        for (int column = 1; column <= 42; column++) {
            occupied.put(column, "课" + column + "<br>(1周)<br>教室");
        }
        String html = table(headerRows() + teacherRow("李四", occupied));

        List<ParsedCell> cells = parser.parse(html).get(0).cells();

        assertThat(cells).hasSize(42);
        for (int column = 1; column <= 42; column++) {
            ParsedCell cell = cells.get(column - 1);
            int expectedDay = (column - 1) / 6 + 1;
            String expectedSlot = KbtableParser.SLOT_CODES.get((column - 1) % 6);
            assertThat(cell.dayOfWeek()).as("column %d day", column).isEqualTo(expectedDay);
            assertThat(cell.slotCode()).as("column %d slot", column).isEqualTo(expectedSlot);
        }
    }

    @Test
    void blankCellsProduceNoOccupancy() {
        String html = table(headerRows() + teacherRow("王五", java.util.Map.of()));

        List<ParsedTeacherRow> rows = parser.parse(html);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).teacherName()).isEqualTo("王五");
        assertThat(rows.get(0).cells()).isEmpty();
    }

    @Test
    void headerRowsAreNotTreatedAsTeachers() {
        String html = table(headerRows() + teacherRow("张三", java.util.Map.of(1, "课<br>(1周)<br>教室")));

        assertThat(parser.parse(html)).hasSize(1);
    }

    @Test
    void rowWithWrongColumnCountIsSkipped() {
        // 只有 8 列的行不是教师数据行，跳过而不是猜位置。
        String shortRow = "<tr><td><nobr>残缺</nobr></td>" + "<td>a</td>".repeat(7) + "</tr>";
        String html = table(headerRows() + shortRow
                + teacherRow("张三", java.util.Map.of(1, "课<br>(1周)<br>教室")));

        List<ParsedTeacherRow> rows = parser.parse(html);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).teacherName()).isEqualTo("张三");
    }

    @Test
    void missingOrEmptyTableReturnsNoRows() {
        assertThat(parser.parse(null)).isEmpty();
        assertThat(parser.parse("")).isEmpty();
        assertThat(parser.parse("<html><body><p>无表</p></body></html>")).isEmpty();
    }

    @Test
    void weekRangeWithoutParenthesesIsAlsoParsed() {
        String html = table(headerRows() + teacherRow("张三", java.util.Map.of(
                7, "线性代数<br>数学2301<br>10-12周<br>教三305")));

        ParsedCell cell = parser.parse(html).get(0).cells().get(0);

        assertThat(cell.dayOfWeek()).isEqualTo(2);
        assertThat(cell.slotCode()).isEqualTo("0102");
        assertThat(cell.weekStart()).isEqualTo(10);
        assertThat(cell.weekEnd()).isEqualTo(12);
    }

    @Test
    void missingWeekRangeFallsBackToWholeSemester() {
        String html = table(headerRows() + teacherRow("张三", java.util.Map.of(
                1, "讲座<br>全院<br>待定<br>报告厅")));

        ParsedCell cell = parser.parse(html).get(0).cells().get(0);

        assertThat(cell.weekStart()).isEqualTo(1);
        assertThat(cell.weekEnd()).isEqualTo(30);
    }

    @Test
    void slotCodesAreFixedOrder() {
        assertThat(KbtableParser.SLOT_CODES)
                .containsExactly("0102", "0304", "0506", "0708", "0910", "1112");
        assertThat(KbtableParser.COLUMNS_PER_TEACHER_ROW).isEqualTo(43);
    }
}
