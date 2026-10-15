package com.example.academicprofile.timetable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.example.academicprofile.common.exception.BusinessException;
import com.example.academicprofile.exporting.ExportFile;
import com.example.academicprofile.timetable.TimetableDtos.CommonFreeResponse;
import com.example.academicprofile.timetable.TimetableDtos.CommonFreeSlot;
import com.example.academicprofile.timetable.TimetableDtos.ExcludedTeacher;
import com.example.academicprofile.timetable.TimetableDtos.OccupancyEntry;

/**
 * 课表专用 Excel 导出。与公共空闲查询复用同一个 query(...)，
 * 保证筛选与判定完全一致；不复用既有成果导出接口。
 */
@Service
public class TimetableCommonFreeExcelService {

    private static final int DAYS_PER_WEEK = 7;

    private final TimetableCommonFreeService commonFreeService;

    public TimetableCommonFreeExcelService(TimetableCommonFreeService commonFreeService) {
        this.commonFreeService = commonFreeService;
    }

    public ExportFile export(String semesterCode, int week, Long majorId, List<Long> teacherIds) {
        CommonFreeResponse response = commonFreeService.query(semesterCode, week, majorId, teacherIds);
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CellStyle bold = boldStyle(workbook);
            writeGrid(workbook, response, bold);
            writeOccupancy(workbook, response, bold);
            writeExcluded(workbook, response, bold);
            workbook.write(output);
            return new ExportFile(
                    "timetable-common-free-" + response.semesterCode() + "-week" + week + ".xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray());
        } catch (IOException ex) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate timetable Excel file");
        }
    }

    /** 第一张表：7 天 x 6 节次组的空闲矩阵。 */
    private void writeGrid(Workbook workbook, CommonFreeResponse response, CellStyle bold) {
        Sheet sheet = workbook.createSheet("空闲总览");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("星期");
        for (int index = 0; index < KbtableParser.SLOT_CODES.size(); index++) {
            Cell cell = header.createCell(index + 1);
            cell.setCellValue(KbtableParser.SLOT_CODES.get(index));
            cell.setCellStyle(bold);
        }
        header.getCell(0).setCellStyle(bold);

        if (response.missingImport()) {
            Row note = sheet.createRow(1);
            note.createCell(0).setCellValue("该学期还没有课表数据，全部时段都不能判定为空闲。");
            sheet.setColumnWidth(0, 40 * 256);
            return;
        }

        for (int day = 1; day <= DAYS_PER_WEEK; day++) {
            Row row = sheet.createRow(day);
            row.createCell(0).setCellValue(dayLabel(day));
            for (int index = 0; index < KbtableParser.SLOT_CODES.size(); index++) {
                String slotCode = KbtableParser.SLOT_CODES.get(index);
                CommonFreeSlot slot = findSlot(response.slots(), day, slotCode);
                row.createCell(index + 1).setCellValue(slotText(slot));
            }
        }
        sheet.setColumnWidth(0, 10 * 256);
        for (int index = 1; index <= KbtableParser.SLOT_CODES.size(); index++) {
            sheet.setColumnWidth(index, 30 * 256);
        }
    }

    /** 第二张表：占用明细，与 PRD 要求一致（教师、课程、教室、星期、节次、周次、占用原文）。 */
    private void writeOccupancy(Workbook workbook, CommonFreeResponse response, CellStyle bold) {
        Sheet sheet = workbook.createSheet("占用明细");
        String[] headers = {"星期", "节次组", "教师", "课程", "教室", "周次", "占用原文"};
        Row header = sheet.createRow(0);
        for (int index = 0; index < headers.length; index++) {
            Cell cell = header.createCell(index);
            cell.setCellValue(headers[index]);
            cell.setCellStyle(bold);
        }
        int rowIndex = 1;
        for (CommonFreeSlot slot : response.slots()) {
            for (OccupancyEntry entry : slot.occupancies()) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(dayLabel(slot.dayOfWeek()));
                row.createCell(1).setCellValue(slot.slotCode());
                row.createCell(2).setCellValue(nullToEmpty(entry.teacherDisplayName()));
                row.createCell(3).setCellValue(nullToEmpty(entry.courseName()));
                row.createCell(4).setCellValue(nullToEmpty(entry.roomName()));
                row.createCell(5).setCellValue("第 " + response.week() + " 周");
                row.createCell(6).setCellValue(nullToEmpty(entry.occupancyText()));
            }
        }
        for (int index = 0; index < headers.length; index++) {
            sheet.setColumnWidth(index, index == 6 ? 50 * 256 : 16 * 256);
        }
    }

    /** 第三张表：未参与判断的教师及其原因。 */
    private void writeExcluded(Workbook workbook, CommonFreeResponse response, CellStyle bold) {
        Sheet sheet = workbook.createSheet("未参与判断");
        String[] headers = {"教师", "原因码", "说明"};
        Row header = sheet.createRow(0);
        for (int index = 0; index < headers.length; index++) {
            Cell cell = header.createCell(index);
            cell.setCellValue(headers[index]);
            cell.setCellStyle(bold);
        }
        int rowIndex = 1;
        for (ExcludedTeacher teacher : response.excludedTeachers()) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(nullToEmpty(teacher.sourceName()));
            row.createCell(1).setCellValue(nullToEmpty(teacher.reason()));
            row.createCell(2).setCellValue(reasonLabel(teacher.reason()));
        }
        sheet.setColumnWidth(0, 20 * 256);
        sheet.setColumnWidth(1, 24 * 256);
        sheet.setColumnWidth(2, 32 * 256);
    }

    private static CommonFreeSlot findSlot(List<CommonFreeSlot> slots, int day, String slotCode) {
        for (CommonFreeSlot slot : slots) {
            if (slot.dayOfWeek() == day && slotCode.equals(slot.slotCode())) {
                return slot;
            }
        }
        return null;
    }

    /** 缺失的格子按“不可用”写，绝不写成空闲。 */
    private static String slotText(CommonFreeSlot slot) {
        if (slot == null) {
            return "无数据";
        }
        if (slot.free()) {
            return "空闲";
        }
        if (slot.occupancies().isEmpty()) {
            return "不可用";
        }
        StringBuilder text = new StringBuilder();
        for (OccupancyEntry entry : slot.occupancies()) {
            if (text.length() > 0) {
                text.append("；");
            }
            text.append(nullToEmpty(entry.teacherDisplayName()))
                    .append("：")
                    .append(nullToEmpty(entry.courseName()));
            if (entry.roomName() != null && !entry.roomName().isBlank()) {
                text.append("（").append(entry.roomName()).append("）");
            }
        }
        return text.toString();
    }

    private static String reasonLabel(String reason) {
        if ("NO_TIMETABLE_MAPPING".equals(reason)) {
            return "未建立课表映射";
        }
        if ("ABSENT_FROM_IMPORT".equals(reason)) {
            return "本学期导入中没有该教师";
        }
        return nullToEmpty(reason);
    }

    private static String dayLabel(int dayOfWeek) {
        String[] labels = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        return dayOfWeek >= 1 && dayOfWeek <= labels.length
                ? labels[dayOfWeek - 1]
                : "第 " + dayOfWeek + " 天";
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static CellStyle boldStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }
}
