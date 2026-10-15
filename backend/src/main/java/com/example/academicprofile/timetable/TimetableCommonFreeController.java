package com.example.academicprofile.timetable;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.List;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.academicprofile.common.ApiResponse;
import com.example.academicprofile.common.exception.BusinessException;
import com.example.academicprofile.exporting.ExportFile;
import com.example.academicprofile.organization.Major;
import com.example.academicprofile.organization.OrganizationDtos.TeacherOption;
import com.example.academicprofile.organization.OrganizationService;
import com.example.academicprofile.timetable.TimetableDtos.CommonFreeResponse;

/**
 * 公共空闲查询。全部接口要求登录本系统；
 * `/api/timetable/**` 无独立规则，由 SecurityConfig 的 anyRequest().authenticated() 覆盖。
 * 只读快照，不接触教务，也不涉及任何登录态。
 */
@RestController
@RequestMapping("/api/timetable/common-free")
public class TimetableCommonFreeController {

    private final TimetableCommonFreeService service;
    private final TimetableCommonFreeExcelService excelService;
    private final OrganizationService organizationService;

    public TimetableCommonFreeController(
            TimetableCommonFreeService service,
            TimetableCommonFreeExcelService excelService,
            OrganizationService organizationService) {
        this.service = service;
        this.excelService = excelService;
        this.organizationService = organizationService;
    }

    /** 查询下拉只返回专业名称和教师显示名，登录用户都可读取。 */
    @GetMapping("/options")
    public ApiResponse<QueryOptions> options() {
        return ApiResponse.ok(new QueryOptions(
                organizationService.listMajors(false),
                organizationService.listTeachers()));
    }

    /** majorId 与 teacherIds 二选一；teacherIds 为逗号分隔的 teacher_profile.id。 */
    @GetMapping
    public ApiResponse<CommonFreeResponse> commonFree(
            @RequestParam String semesterCode,
            @RequestParam int week,
            @RequestParam(required = false) Long majorId,
            @RequestParam(required = false) String teacherIds) {
        return ApiResponse.ok(service.query(
                semesterCode, week, majorId, parseTeacherIds(teacherIds)));
    }

    /** 导出当前查询：筛选与判定复用 commonFree 的同一套逻辑。 */
    @GetMapping("/excel")
    public ResponseEntity<ByteArrayResource> exportExcel(
            @RequestParam String semesterCode,
            @RequestParam int week,
            @RequestParam(required = false) Long majorId,
            @RequestParam(required = false) String teacherIds) {
        ExportFile file = excelService.export(
                semesterCode, week, majorId, parseTeacherIds(teacherIds));
        String encodedName = URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .body(new ByteArrayResource(file.content()));
    }

    public record QueryOptions(List<Major> majors, List<TeacherOption> teachers) {
    }

    private static List<Long> parseTeacherIds(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(piece -> !piece.isEmpty())
                .map(piece -> {
                    try {
                        return Long.valueOf(piece);
                    } catch (NumberFormatException ex) {
                        throw new BusinessException(HttpStatus.BAD_REQUEST,
                                "teacherIds must be comma separated ids");
                    }
                })
                .toList();
    }
}
