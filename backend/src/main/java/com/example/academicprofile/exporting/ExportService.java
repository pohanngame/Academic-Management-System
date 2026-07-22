package com.example.academicprofile.exporting;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.academicprofile.achievement.Certificate;
import com.example.academicprofile.achievement.CertificateMapper;
import com.example.academicprofile.achievement.Paper;
import com.example.academicprofile.achievement.PaperMapper;
import com.example.academicprofile.achievement.Patent;
import com.example.academicprofile.achievement.PatentMapper;
import com.example.academicprofile.achievement.Project;
import com.example.academicprofile.achievement.ProjectMapper;
import com.example.academicprofile.achievement.TeachingCourse;
import com.example.academicprofile.achievement.TeachingCourseMapper;
import com.example.academicprofile.common.exception.BusinessException;
import com.example.academicprofile.profileblock.AcademicQualification;
import com.example.academicprofile.profileblock.AcademicQualificationMapper;
import com.example.academicprofile.profileblock.ProfessionalService;
import com.example.academicprofile.profileblock.ProfessionalServiceMapper;
import com.example.academicprofile.profileblock.ResearchArea;
import com.example.academicprofile.profileblock.ResearchAreaMapper;
import com.example.academicprofile.profileblock.TeachingArea;
import com.example.academicprofile.profileblock.TeachingAreaMapper;
import com.example.academicprofile.profileblock.WorkingExperience;
import com.example.academicprofile.profileblock.WorkingExperienceMapper;
import com.example.academicprofile.teacher.TeacherProfile;
import com.example.academicprofile.teacher.TeacherProfileService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ExportService {

    private static final String CONTENT_TYPE_EXCEL = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String CONTENT_TYPE_WORD = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final ExportTemplateMapper exportTemplateMapper;
    private final TeacherProfileService teacherProfileService;
    private final AcademicQualificationMapper academicQualificationMapper;
    private final TeachingAreaMapper teachingAreaMapper;
    private final ResearchAreaMapper researchAreaMapper;
    private final ProfessionalServiceMapper professionalServiceMapper;
    private final WorkingExperienceMapper workingExperienceMapper;
    private final ProjectMapper projectMapper;
    private final TeachingCourseMapper teachingCourseMapper;
    private final PaperMapper paperMapper;
    private final PatentMapper patentMapper;
    private final CertificateMapper certificateMapper;
    private final ObjectMapper objectMapper;
    private final Map<String, ModuleDefinition<?>> modules;

    public ExportService(
            ExportTemplateMapper exportTemplateMapper,
            TeacherProfileService teacherProfileService,
            AcademicQualificationMapper academicQualificationMapper,
            TeachingAreaMapper teachingAreaMapper,
            ResearchAreaMapper researchAreaMapper,
            ProfessionalServiceMapper professionalServiceMapper,
            WorkingExperienceMapper workingExperienceMapper,
            ProjectMapper projectMapper,
            TeachingCourseMapper teachingCourseMapper,
            PaperMapper paperMapper,
            PatentMapper patentMapper,
            CertificateMapper certificateMapper,
            ObjectMapper objectMapper) {
        this.exportTemplateMapper = exportTemplateMapper;
        this.teacherProfileService = teacherProfileService;
        this.academicQualificationMapper = academicQualificationMapper;
        this.teachingAreaMapper = teachingAreaMapper;
        this.researchAreaMapper = researchAreaMapper;
        this.professionalServiceMapper = professionalServiceMapper;
        this.workingExperienceMapper = workingExperienceMapper;
        this.projectMapper = projectMapper;
        this.teachingCourseMapper = teachingCourseMapper;
        this.paperMapper = paperMapper;
        this.patentMapper = patentMapper;
        this.certificateMapper = certificateMapper;
        this.objectMapper = objectMapper;
        this.modules = buildModules();
    }

    public List<ExportModuleDefinition> getFieldDefinitions() {
        return modules.values().stream()
                .map(ModuleDefinition::toResponse)
                .toList();
    }

    public ExportFile exportExcel(Long teacherId, ExportRequest request) {
        List<ValidatedModuleSelection<?>> selections = validateSelections(request.modules());
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (ValidatedModuleSelection<?> selection : selections) {
                writeExcelSheet(workbook, teacherId, selection);
            }
            workbook.write(output);
            return new ExportFile(defaultFileName("xlsx"), CONTENT_TYPE_EXCEL, output.toByteArray());
        } catch (IOException ex) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate Excel file");
        }
    }

    public ExportFile exportWord(Long teacherId, ExportRequest request) {
        List<ValidatedModuleSelection<?>> selections = validateSelections(request.modules());
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            writeTitle(document, "个人学术简介");
            for (ValidatedModuleSelection<?> selection : selections) {
                writeWordSection(document, teacherId, selection);
            }
            document.write(output);
            return new ExportFile(defaultFileName("docx"), CONTENT_TYPE_WORD, output.toByteArray());
        } catch (IOException ex) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate Word file");
        }
    }

    public List<ExportTemplateResponse> listTemplates(Long teacherId) {
        return exportTemplateMapper.selectList(new LambdaQueryWrapper<ExportTemplate>()
                .eq(ExportTemplate::getTeacherId, teacherId)
                .eq(ExportTemplate::getDeleted, Boolean.FALSE)
                .orderByDesc(ExportTemplate::getId))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ExportTemplateResponse saveTemplate(Long teacherId, ExportTemplateRequest request) {
        List<ValidatedModuleSelection<?>> selections = validateSelections(request.modules());
        ExportTemplate template = new ExportTemplate();
        template.setTeacherId(teacherId);
        template.setTemplateName(request.templateName());
        template.setExportType(request.exportType().name());
        template.setSelectedModules(writeJson(selections.stream()
                .map(selection -> selection.module().key())
                .toList()));
        template.setSelectedFields(writeJson(toSelectedFields(selections)));
        template.setFieldOrder(writeJson(toFieldOrder(selections)));
        template.setDeleted(Boolean.FALSE);
        exportTemplateMapper.insert(template);
        return toResponse(template);
    }

    @Transactional
    public void deleteTemplate(Long teacherId, Long id) {
        ExportTemplate template = exportTemplateMapper.selectById(id);
        if (template == null || !teacherId.equals(template.getTeacherId()) || Boolean.TRUE.equals(template.getDeleted())) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Export template not found");
        }
        template.setDeleted(Boolean.TRUE);
        exportTemplateMapper.updateById(template);
    }

    private void writeExcelSheet(XSSFWorkbook workbook, Long teacherId, ValidatedModuleSelection<?> selection) {
        XSSFSheet sheet = workbook.createSheet(safeSheetName(selection.module().label()));
        int rowIndex = 0;
        org.apache.poi.ss.usermodel.Row header = sheet.createRow(rowIndex++);
        for (int i = 0; i < selection.fields().size(); i++) {
            header.createCell(i).setCellValue(selection.fields().get(i).label());
        }
        for (Map<String, Object> rowData : selectionRows(teacherId, selection)) {
            org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIndex++);
            for (int i = 0; i < selection.fields().size(); i++) {
                Object value = rowData.get(selection.fields().get(i).key());
                row.createCell(i).setCellValue(formatValue(value));
            }
        }
        for (int i = 0; i < selection.fields().size(); i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void writeWordSection(XWPFDocument document, Long teacherId, ValidatedModuleSelection<?> selection) {
        XWPFParagraph heading = document.createParagraph();
        XWPFRun headingRun = heading.createRun();
        headingRun.setBold(true);
        headingRun.setFontSize(14);
        headingRun.setText(selection.module().label());

        XWPFTable table = document.createTable();
        XWPFTableRow header = table.getRow(0);
        ensureCells(header, selection.fields().size());
        for (int i = 0; i < selection.fields().size(); i++) {
            setCellText(header.getCell(i), selection.fields().get(i).label());
        }
        for (Map<String, Object> rowData : selectionRows(teacherId, selection)) {
            XWPFTableRow row = table.createRow();
            ensureCells(row, selection.fields().size());
            for (int i = 0; i < selection.fields().size(); i++) {
                Object value = rowData.get(selection.fields().get(i).key());
                setCellText(row.getCell(i), formatValue(value));
            }
        }
        document.createParagraph();
    }

    private void writeTitle(XWPFDocument document, String title) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = paragraph.createRun();
        run.setBold(true);
        run.setFontSize(18);
        run.setText(title);
    }

    private void ensureCells(XWPFTableRow row, int count) {
        while (row.getTableCells().size() < count) {
            row.createCell();
        }
    }

    private void setCellText(XWPFTableCell cell, String text) {
        cell.removeParagraph(0);
        XWPFParagraph paragraph = cell.addParagraph();
        XWPFRun run = paragraph.createRun();
        run.setText(text == null ? "" : text);
    }

    private List<ValidatedModuleSelection<?>> validateSelections(List<ExportModuleSelection> requestedModules) {
        if (requestedModules == null || requestedModules.isEmpty()) {
            throw new BusinessException("At least one module is required");
        }
        List<ValidatedModuleSelection<?>> selections = new ArrayList<>();
        for (ExportModuleSelection requested : requestedModules) {
            ModuleDefinition<?> module = modules.get(requested.moduleKey());
            if (module == null) {
                throw new BusinessException("Unsupported export module: " + requested.moduleKey());
            }
            if (requested.fields() == null || requested.fields().isEmpty()) {
                throw new BusinessException("At least one field is required for module: " + requested.moduleKey());
            }
            List<FieldDefinition<?>> fields = new ArrayList<>();
            for (String fieldKey : requested.fields()) {
                FieldDefinition<?> field = module.field(fieldKey);
                if (field == null) {
                    throw new BusinessException("Unsupported export field: " + requested.moduleKey() + "." + fieldKey);
                }
                fields.add(field);
            }
            selections.add(new ValidatedModuleSelection(module, fields));
        }
        return selections;
    }

    private List<Map<String, Object>> selectionRows(Long teacherId, ValidatedModuleSelection<?> selection) {
        return rowsFor(teacherId, rawSelection(selection));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> List<Map<String, Object>> rowsFor(Long teacherId, ValidatedModuleSelection<T> selection) {
        List<T> data = selection.module().loader().apply(teacherId);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (T item : data) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (FieldDefinition field : selection.fields()) {
                row.put(field.key(), field.reader().apply(item));
            }
            rows.add(row);
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    private <T> ValidatedModuleSelection<T> rawSelection(ValidatedModuleSelection<?> selection) {
        return (ValidatedModuleSelection<T>) selection;
    }

    private Map<String, List<String>> toSelectedFields(List<ValidatedModuleSelection<?>> selections) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (ValidatedModuleSelection<?> selection : selections) {
            result.put(selection.module().key(), selection.fields().stream().map(FieldDefinition::key).toList());
        }
        return result;
    }

    private List<ExportModuleSelection> toFieldOrder(List<ValidatedModuleSelection<?>> selections) {
        return selections.stream()
                .map(selection -> new ExportModuleSelection(
                        selection.module().key(),
                        selection.fields().stream().map(FieldDefinition::key).toList()))
                .toList();
    }

    private ExportTemplateResponse toResponse(ExportTemplate template) {
        return new ExportTemplateResponse(
                template.getId(),
                template.getTemplateName(),
                ExportType.valueOf(template.getExportType()),
                readFieldOrder(template.getFieldOrder()),
                template.getCreatedAt(),
                template.getUpdatedAt());
    }

    private List<ExportModuleSelection> readFieldOrder(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<ExportModuleSelection>>() {
            });
        } catch (JsonProcessingException ex) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read export template");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save export template");
        }
    }

    private String defaultFileName(String extension) {
        return "academic-profile-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "." + extension;
    }

    private String safeSheetName(String name) {
        String cleaned = name.replaceAll("[\\\\/?*\\[\\]:]", " ");
        return cleaned.length() > 31 ? cleaned.substring(0, 31) : cleaned;
    }

    private String formatValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private Map<String, ModuleDefinition<?>> buildModules() {
        Map<String, ModuleDefinition<?>> result = new LinkedHashMap<>();
        result.put("teacherProfile", new ModuleDefinition<>("teacherProfile", "教师资料", false,
                teacherId -> List.of(teacherProfileService.getByTeacherId(teacherId)),
                List.of(
                        field("displayName", "姓名", TeacherProfile::getDisplayName),
                        field("title", "职称", TeacherProfile::getTitle),
                        field("department", "部门", TeacherProfile::getDepartment),
                        field("phone", "电话", TeacherProfile::getPhone),
                        field("office", "办公室", TeacherProfile::getOffice),
                        field("profileEmail", "展示邮箱", TeacherProfile::getProfileEmail),
                        field("biography", "个人简介", TeacherProfile::getBiography),
                        field("publicSlug", "公开路径", TeacherProfile::getPublicSlug))));
        result.put("academicQualifications", new ModuleDefinition<>("academicQualifications", "学历", true,
                teacherId -> academicQualificationMapper.selectList(new LambdaQueryWrapper<AcademicQualification>()
                        .eq(AcademicQualification::getTeacherId, teacherId)
                        .orderByAsc(AcademicQualification::getSortOrder)
                        .orderByDesc(AcademicQualification::getId)),
                List.of(
                        field("degree", "学位/学历", AcademicQualification::getDegree),
                        field("institution", "学校/机构", AcademicQualification::getInstitution),
                        field("major", "专业", AcademicQualification::getMajor),
                        field("startDate", "开始日期", AcademicQualification::getStartDate),
                        field("endDate", "结束日期", AcademicQualification::getEndDate),
                        field("description", "说明", AcademicQualification::getDescription))));
        result.put("teachingAreas", new ModuleDefinition<>("teachingAreas", "教学方向", true,
                teacherId -> teachingAreaMapper.selectList(new LambdaQueryWrapper<TeachingArea>()
                        .eq(TeachingArea::getTeacherId, teacherId)
                        .orderByAsc(TeachingArea::getSortOrder)
                        .orderByDesc(TeachingArea::getId)),
                List.of(
                        field("name", "方向名称", TeachingArea::getName),
                        field("description", "说明", TeachingArea::getDescription))));
        result.put("researchAreas", new ModuleDefinition<>("researchAreas", "研究方向", true,
                teacherId -> researchAreaMapper.selectList(new LambdaQueryWrapper<ResearchArea>()
                        .eq(ResearchArea::getTeacherId, teacherId)
                        .orderByAsc(ResearchArea::getSortOrder)
                        .orderByDesc(ResearchArea::getId)),
                List.of(
                        field("name", "方向名称", ResearchArea::getName),
                        field("description", "说明", ResearchArea::getDescription))));
        result.put("professionalServices", new ModuleDefinition<>("professionalServices", "专业服务", true,
                teacherId -> professionalServiceMapper.selectList(new LambdaQueryWrapper<ProfessionalService>()
                        .eq(ProfessionalService::getTeacherId, teacherId)
                        .orderByAsc(ProfessionalService::getSortOrder)
                        .orderByDesc(ProfessionalService::getId)),
                List.of(
                        field("title", "服务名称", ProfessionalService::getTitle),
                        field("organization", "机构/组织", ProfessionalService::getOrganization),
                        field("role", "角色", ProfessionalService::getRole),
                        field("startDate", "开始日期", ProfessionalService::getStartDate),
                        field("endDate", "结束日期", ProfessionalService::getEndDate),
                        field("description", "说明", ProfessionalService::getDescription))));
        result.put("workingExperiences", new ModuleDefinition<>("workingExperiences", "工作经历", true,
                teacherId -> workingExperienceMapper.selectList(new LambdaQueryWrapper<WorkingExperience>()
                        .eq(WorkingExperience::getTeacherId, teacherId)
                        .orderByAsc(WorkingExperience::getSortOrder)
                        .orderByDesc(WorkingExperience::getId)),
                List.of(
                        field("organization", "单位", WorkingExperience::getOrganization),
                        field("position", "职位", WorkingExperience::getPosition),
                        field("startDate", "开始日期", WorkingExperience::getStartDate),
                        field("endDate", "结束日期", WorkingExperience::getEndDate),
                        field("description", "说明", WorkingExperience::getDescription))));
        result.put("projects", new ModuleDefinition<>("projects", "科研项目", true,
                teacherId -> projectMapper.selectList(new LambdaQueryWrapper<Project>()
                        .eq(Project::getTeacherId, teacherId)
                        .orderByAsc(Project::getSortOrder)
                        .orderByDesc(Project::getId)),
                List.of(
                        field("projectName", "项目名称", Project::getProjectName),
                        field("source", "项目来源", Project::getSource),
                        field("role", "承担角色", Project::getRole),
                        field("startDate", "开始日期", Project::getStartDate),
                        field("endDate", "结束日期", Project::getEndDate),
                        field("fundingAmount", "经费金额", Project::getFundingAmount),
                        field("status", "状态", Project::getStatus),
                        field("description", "说明", Project::getDescription))));
        result.put("teachingCourses", new ModuleDefinition<>("teachingCourses", "授课记录", true,
                teacherId -> teachingCourseMapper.selectList(new LambdaQueryWrapper<TeachingCourse>()
                        .eq(TeachingCourse::getTeacherId, teacherId)
                        .orderByAsc(TeachingCourse::getSortOrder)
                        .orderByDesc(TeachingCourse::getId)),
                List.of(
                        field("courseName", "课程名称", TeachingCourse::getCourseName),
                        field("semester", "学期", TeachingCourse::getSemester),
                        field("className", "班级", TeachingCourse::getClassName),
                        field("teachingTarget", "授课对象", TeachingCourse::getTeachingTarget),
                        field("hours", "学时", TeachingCourse::getHours),
                        field("description", "说明", TeachingCourse::getDescription))));
        result.put("papers", new ModuleDefinition<>("papers", "论文/学术成果", true,
                teacherId -> paperMapper.selectList(new LambdaQueryWrapper<Paper>()
                        .eq(Paper::getTeacherId, teacherId)
                        .orderByAsc(Paper::getSortOrder)
                        .orderByDesc(Paper::getId)),
                List.of(
                        field("title", "成果标题", Paper::getTitle),
                        field("authors", "作者", Paper::getAuthors),
                        field("publicationName", "期刊/会议/出版物", Paper::getPublicationName),
                        field("publicationType", "成果类型", Paper::getPublicationType),
                        field("publishYear", "发表年份", Paper::getPublishYear),
                        field("doi", "DOI", Paper::getDoi),
                        field("volume", "卷", Paper::getVolume),
                        field("issue", "期", Paper::getIssue),
                        field("pages", "页码", Paper::getPages),
                        field("publisher", "出版社", Paper::getPublisher),
                        field("url", "URL", Paper::getUrl),
                        field("abstractText", "摘要", Paper::getAbstractText),
                        field("keywords", "关键词", Paper::getKeywords))));
        result.put("patents", new ModuleDefinition<>("patents", "专利", true,
                teacherId -> patentMapper.selectList(new LambdaQueryWrapper<Patent>()
                        .eq(Patent::getTeacherId, teacherId)
                        .orderByAsc(Patent::getSortOrder)
                        .orderByDesc(Patent::getId)),
                List.of(
                        field("patentName", "专利名称", Patent::getPatentName),
                        field("patentNumber", "专利号", Patent::getPatentNumber),
                        field("patentType", "专利类型", Patent::getPatentType),
                        field("status", "状态", Patent::getStatus),
                        field("applicationDate", "申请日期", Patent::getApplicationDate),
                        field("authorizationDate", "授权日期", Patent::getAuthorizationDate),
                        field("inventors", "发明人", Patent::getInventors),
                        field("description", "说明", Patent::getDescription))));
        result.put("certificates", new ModuleDefinition<>("certificates", "证书", true,
                teacherId -> certificateMapper.selectList(new LambdaQueryWrapper<Certificate>()
                        .eq(Certificate::getTeacherId, teacherId)
                        .orderByAsc(Certificate::getSortOrder)
                        .orderByDesc(Certificate::getId)),
                List.of(
                        field("certificateName", "证书名称", Certificate::getCertificateName),
                        field("certificateType", "证书类型", Certificate::getCertificateType),
                        field("issuingAuthority", "颁发机构", Certificate::getIssuingAuthority),
                        field("issueDate", "颁发日期", Certificate::getIssueDate),
                        field("description", "说明", Certificate::getDescription))));
        return result;
    }

    private <T> FieldDefinition<T> field(String key, String label, Function<T, Object> reader) {
        return new FieldDefinition<>(key, label, reader);
    }

    private record ModuleDefinition<T>(
            String key,
            String label,
            boolean multiple,
            Function<Long, List<T>> loader,
            List<FieldDefinition<T>> fields) {

        ExportModuleDefinition toResponse() {
            return new ExportModuleDefinition(
                    key,
                    label,
                    multiple,
                    fields.stream()
                            .map(field -> new ExportFieldDefinition(field.key(), field.label()))
                            .toList());
        }

        FieldDefinition<T> field(String fieldKey) {
            return fields.stream()
                    .filter(field -> field.key().equals(fieldKey))
                    .findFirst()
                    .orElse(null);
        }
    }

    private record FieldDefinition<T>(String key, String label, Function<T, Object> reader) {
    }

    private record ValidatedModuleSelection<T>(ModuleDefinition<T> module, List<FieldDefinition<T>> fields) {
    }
}
