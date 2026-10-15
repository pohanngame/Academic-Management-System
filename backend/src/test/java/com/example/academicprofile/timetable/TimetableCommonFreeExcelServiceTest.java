package com.example.academicprofile.timetable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.academicprofile.exporting.ExportFile;
import com.example.academicprofile.timetable.TimetableDtos.CommonFreeResponse;
import com.example.academicprofile.timetable.TimetableDtos.CommonFreeSlot;
import com.example.academicprofile.timetable.TimetableDtos.ExcludedTeacher;
import com.example.academicprofile.timetable.TimetableDtos.OccupancyEntry;

/**
 * 课表 Excel：必须与公共空闲查询同样的筛选与判定（这里直接断言复用了同一个 query），
 * 且产物是真正的 xlsx。
 */
class TimetableCommonFreeExcelServiceTest {

    private TimetableCommonFreeService commonFree;
    private TimetableCommonFreeExcelService excelService;

    @BeforeEach
    void setUp() {
        commonFree = mock(TimetableCommonFreeService.class);
        excelService = new TimetableCommonFreeExcelService(commonFree);
    }

    private static CommonFreeResponse response(boolean missingImport) {
        OccupancyEntry entry = new OccupancyEntry(
                7L, "张三", "数据结构", "教一101", "数据结构\r\n计科2301\r\n(1-16周)\r\n教一101");
        List<CommonFreeSlot> slots = List.of(
                new CommonFreeSlot(2, "0304", false, List.of(entry)),
                new CommonFreeSlot(2, "0506", true, List.of()));
        List<ExcludedTeacher> excluded = List.of(
                new ExcludedTeacher("李四", "NO_TIMETABLE_MAPPING"),
                new ExcludedTeacher("王五", "ABSENT_FROM_IMPORT"));
        return new CommonFreeResponse("2026-2027-1", 5, missingImport, slots, excluded);
    }

    @Test
    void exportReusesTheSameQueryAsTheCommonFreeEndpoint() {
        when(commonFree.query(anyString(), anyInt(), any(), any())).thenReturn(response(false));

        excelService.export("2026-2027-1", 5, 3L, List.of());

        // 与 GET /api/timetable/common-free 完全相同的入参。
        verify(commonFree).query("2026-2027-1", 5, 3L, List.of());
    }

    @Test
    void exportProducesAReadableXlsxWithTheThreeSheets() throws Exception {
        when(commonFree.query(anyString(), anyInt(), any(), any())).thenReturn(response(false));

        ExportFile file = excelService.export("2026-2027-1", 5, null, List.of(7L));

        assertThat(file.fileName()).endsWith(".xlsx").contains("2026-2027-1");
        assertThat(file.contentType()).contains("spreadsheetml");
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file.content()))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(3);
            Sheet grid = workbook.getSheet("空闲总览");
            assertThat(grid.getRow(0).getCell(1).getStringCellValue()).isEqualTo("0102");
            // 09-10 这一列不能被排到最前（数字样式键的顺序坑）。
            assertThat(grid.getRow(0).getCell(6).getStringCellValue()).isEqualTo("1112");
            Sheet occupancy = workbook.getSheet("占用明细");
            assertThat(occupancy.getRow(1).getCell(2).getStringCellValue()).isEqualTo("张三");
            assertThat(occupancy.getRow(1).getCell(3).getStringCellValue()).isEqualTo("数据结构");
            assertThat(occupancy.getRow(1).getCell(6).getStringCellValue()).contains("数据结构");
        }
    }

    @Test
    void missingImportIsWrittenExplicitlyInsteadOfAllFree() throws Exception {
        when(commonFree.query(anyString(), anyInt(), any(), any())).thenReturn(response(true));

        ExportFile file = excelService.export("2026-2027-1", 5, null, List.of(7L));

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file.content()))) {
            Sheet grid = workbook.getSheet("空闲总览");
            assertThat(grid.getRow(1).getCell(0).getStringCellValue()).contains("还没有课表数据");
        }
    }

    @Test
    void excludedTeachersAreListedWithTheirReasons() throws Exception {
        when(commonFree.query(anyString(), anyInt(), any(), any())).thenReturn(response(false));

        ExportFile file = excelService.export("2026-2027-1", 5, null, List.of(7L));

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file.content()))) {
            Sheet excluded = workbook.getSheet("未参与判断");
            assertThat(excluded.getRow(1).getCell(0).getStringCellValue()).isEqualTo("李四");
            assertThat(excluded.getRow(1).getCell(1).getStringCellValue()).isEqualTo("NO_TIMETABLE_MAPPING");
            assertThat(excluded.getRow(2).getCell(1).getStringCellValue()).isEqualTo("ABSENT_FROM_IMPORT");
            assertThat(excluded.getRow(2).getCell(2).getStringCellValue()).isEqualTo("本学期导入中没有该教师");
        }
    }
}
