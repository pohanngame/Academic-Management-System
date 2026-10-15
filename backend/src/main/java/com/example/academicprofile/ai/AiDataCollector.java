package com.example.academicprofile.ai;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.example.academicprofile.access.AccessPermission;
import com.example.academicprofile.access.ResourceAccessAuditService;
import com.example.academicprofile.access.ResourceAuthorizationService;
import com.example.academicprofile.access.ResourceType;
import com.example.academicprofile.achievement.AchievementService;
import com.example.academicprofile.common.exception.BusinessException;
import com.example.academicprofile.file.FileBusinessType;
import com.example.academicprofile.file.FileMetadata;
import com.example.academicprofile.file.FileMetadataMapper;
import com.example.academicprofile.ocr.OcrTask;
import com.example.academicprofile.ocr.OcrTaskMapper;
import com.example.academicprofile.profileblock.ProfileBlockService;
import com.example.academicprofile.publication.PaperRatingVisibility;
import com.example.academicprofile.security.CurrentUser;
import com.example.academicprofile.teacher.TeacherProfileResponse;
import com.example.academicprofile.teacher.TeacherProfileService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.util.StringUtils;

@Component
public class AiDataCollector {
    /** 课表占用模块：只在本次请求选中时采集，未选中时载荷不含任何课表数据。 */
    static final String TIMETABLE_MODULE = "timetableOccupancy";

    private static final Set<String> INTERNAL_FIELDS = Set.of(
            "id", "userId", "teacherId", "avatarFileId", "createdAt", "updatedAt",
            "sortOrder", "isPublic", "publicEnabled", "publicSlug", "fieldVisibilityConfig");
    private static final Map<String, Set<String>> ALLOWED_FIELDS = Map.ofEntries(
            Map.entry("teacherProfile", Set.of("displayName", "department", "title", "biography")),
            Map.entry("academicQualifications", Set.of("degree", "institution", "major", "startDate", "endDate", "description")),
            Map.entry("teachingAreas", Set.of("name", "description")),
            Map.entry("researchAreas", Set.of("name", "description")),
            Map.entry("professionalServices", Set.of("title", "organization", "role", "startDate", "endDate", "description")),
            Map.entry("workingExperiences", Set.of("organization", "position", "startDate", "endDate", "description")),
            Map.entry("projects", Set.of("projectName", "source", "role", "startDate", "endDate", "fundingAmount", "status", "description")),
            Map.entry("teachingCourses", Set.of("courseName", "semester", "className", "teachingTarget", "hours", "description")),
            Map.entry("papers", Set.of("title", "authors", "publicationName", "publicationType", "venueType",
                    "venueAbbreviation", "publishYear", "doi", "volume", "issue", "pages", "publisher", "url",
                    "abstractText", "keywords", "ccfRank", "ccfCategoryName", "ccfDataYear", "jcrDataYear",
                    "journalImpactFactor", "jcrQuartile", "jcrSubject")),
            Map.entry("patents", Set.of("patentName", "patentNumber", "patentType", "status", "applicationDate", "authorizationDate", "inventors", "description")),
            Map.entry("certificates", Set.of("certificateName", "certificateType", "competitionCategory", "issuingAuthority", "issueDate", "description")),
            Map.entry(TIMETABLE_MODULE, Set.of("teacherName", "courseName", "roomName",
                    "dayOfWeek", "slotCode", "week", "occupancyText")));
    private static final Map<String, ResourceType> RESOURCE_TYPES = Map.ofEntries(
            Map.entry("academicQualifications", ResourceType.ACADEMIC_QUALIFICATION),
            Map.entry("teachingAreas", ResourceType.TEACHING_AREA),
            Map.entry("researchAreas", ResourceType.RESEARCH_AREA),
            Map.entry("professionalServices", ResourceType.PROFESSIONAL_SERVICE),
            Map.entry("workingExperiences", ResourceType.WORKING_EXPERIENCE),
            Map.entry("projects", ResourceType.PROJECT),
            Map.entry("teachingCourses", ResourceType.TEACHING_COURSE),
            Map.entry("papers", ResourceType.PAPER),
            Map.entry("patents", ResourceType.PATENT),
            Map.entry("certificates", ResourceType.CERTIFICATE));

    private final TeacherProfileService teacherProfileService;
    private final ProfileBlockService profileBlockService;
    private final AchievementService achievementService;
    private final ResourceAuthorizationService authorization;
    private final ResourceAccessAuditService audits;
    private final ObjectMapper objectMapper;
    private final FileMetadataMapper files;
    private final OcrTaskMapper ocrTasks;
    private final com.example.academicprofile.timetable.TimetableCommonFreeService commonFreeService;

    public AiDataCollector(
            TeacherProfileService teacherProfileService,
            ProfileBlockService profileBlockService,
            AchievementService achievementService,
            ResourceAuthorizationService authorization,
            ResourceAccessAuditService audits,
            ObjectMapper objectMapper,
            FileMetadataMapper files,
            OcrTaskMapper ocrTasks,
            com.example.academicprofile.timetable.TimetableCommonFreeService commonFreeService) {
        this.teacherProfileService = teacherProfileService;
        this.profileBlockService = profileBlockService;
        this.achievementService = achievementService;
        this.authorization = authorization;
        this.audits = audits;
        this.objectMapper = objectMapper;
        this.files = files;
        this.ocrTasks = ocrTasks;
        this.commonFreeService = commonFreeService;
    }

    /**
     * 课表占用：只取本次查询结果。未提供 timetableQuery 时返回空，不回退到任何默认范围。
     * 与公共空闲查询复用同一条判定逻辑，不写 teaching_course。
     */
    private List<Map<String, Object>> collectTimetableOccupancy(AiGenerationRequest request) {
        AiGenerationRequest.TimetableQuery query = request.timetableQuery();
        if (query == null || query.semesterCode() == null || query.semesterCode().isBlank()
                || query.week() == null) {
            return List.of();
        }
        var response = commonFreeService.query(
                query.semesterCode(), query.week(), query.majorId(),
                query.teacherIds() == null ? List.of() : query.teacherIds());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (var slot : response.slots()) {
            for (var entry : slot.occupancies()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("teacherName", entry.teacherDisplayName());
                row.put("courseName", entry.courseName());
                row.put("roomName", entry.roomName());
                row.put("dayOfWeek", slot.dayOfWeek());
                row.put("slotCode", slot.slotCode());
                row.put("week", response.week());
                row.put("occupancyText", entry.occupancyText());
                rows.add(row);
            }
        }
        return rows;
    }

    public Map<String, Object> collect(Long teacherId, List<String> modules) {
        CurrentUser self = new CurrentUser(-1L, teacherId, "", "", "TEACHER", 0L);
        return collect(self, modules, new AiGenerationRequest(null, "", modules)).data();
    }

    public CollectionResult collect(
            CurrentUser actor, List<String> modules, AiGenerationRequest request) {
        List<Long> teachers = request.sourceTeacherIds() == null || request.sourceTeacherIds().isEmpty()
                ? List.of(actor.teacherId())
                : new LinkedHashSet<>(request.sourceTeacherIds()).stream().toList();
        if (teachers.size() > 50 || teachers.stream().anyMatch(id -> id == null || id < 1)) {
            throw new BusinessException("Invalid source teacher selection");
        }
        validateFilters(request);
        Map<String, Object> data = new LinkedHashMap<>();
        List<SourceReference> sources = new ArrayList<>();
        for (String module : modules) {
            Set<String> selected = selectedFields(module, request.selectedFields());
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Long sourceTeacherId : teachers) {
                if ("teacherProfile".equals(module)) {
                    if (!sourceTeacherId.equals(actor.teacherId())) {
                        continue;
                    }
                    Map<String, Object> profile = sanitize(
                            TeacherProfileResponse.from(teacherProfileService.getByTeacherId(sourceTeacherId)),
                            selected);
                    profile.put("sourceTeacherId", sourceTeacherId);
                    rows.add(profile);
                    continue;
                }
                if (TIMETABLE_MODULE.equals(module)) {
                    // 只带本次查询结果；不同教师维度循环，避免重复。
                    if (!sourceTeacherId.equals(teachers.get(0))) {
                        continue;
                    }
                    rows.addAll(collectTimetableOccupancy(request));
                    continue;
                }
                ResourceType type = RESOURCE_TYPES.get(module);
                for (Object raw : load(module, sourceTeacherId)) {
                    Map<String, Object> rawMap = objectMapper.convertValue(raw, new TypeReference<>() {});
                    if ("papers".equals(module)) {
                        PaperRatingVisibility.removeUnverifiedFromAiMap(rawMap);
                    }
                    Long resourceId = ((Number) rawMap.get("id")).longValue();
                    if (!matchesFilters(module, rawMap, request)) {
                        continue;
                    }
                    var decision = authorization.findAuthorized(actor, type, resourceId, AccessPermission.AI_USE)
                            .orElse(null);
                    if (decision == null) {
                        continue;
                    }
                    audits.record(actor, decision.ownerTeacherId(), "AI_USE", type, resourceId,
                            null, decision.grant(), true, null, "AI material generation");
                    Map<String, Object> row = sanitizeMap(rawMap, selected);
                    row.put("sourceTeacherId", sourceTeacherId);
                    rows.add(row);
                    sources.add(new SourceReference(
                            sourceTeacherId, type.name(), resourceId, null,
                            decision.grant() == null ? null : decision.grant().getId(), null, null));
                }
            }
            data.put(module, rows);
        }
        collectAttachments(actor, request.selectedAttachmentIds(), data, sources);
        Set<Long> requestedOthers = teachers.stream()
                .filter(id -> !actor.teacherId().equals(id))
                .collect(java.util.stream.Collectors.toSet());
        Set<Long> contributedOthers = sources.stream()
                .map(SourceReference::teacherId)
                .filter(requestedOthers::contains)
                .collect(java.util.stream.Collectors.toSet());
        if (!requestedOthers.isEmpty() && contributedOthers.isEmpty()) {
            throw new BusinessException("所选人员在当前数据范围内没有可用的 AI 授权资料，请调整资料人员、数据范围或重新申请权限");
        }
        return new CollectionResult(data, sources);
    }

    private void collectAttachments(
            CurrentUser actor,
            List<Long> selectedIds,
            Map<String, Object> data,
            List<SourceReference> sources) {
        if (selectedIds == null || selectedIds.isEmpty()) {
            return;
        }
        List<Long> ids = new LinkedHashSet<>(selectedIds).stream().toList();
        if (ids.size() != selectedIds.size()
                || ids.size() > 20
                || ids.stream().anyMatch(id -> id == null || id < 1)) {
            throw new BusinessException("Invalid attachment selection");
        }
        List<Map<String, Object>> attachmentData = new ArrayList<>();
        for (Long fileId : ids) {
            FileMetadata file = files.selectById(fileId);
            if (file == null
                    || Boolean.TRUE.equals(file.getDeleted())
                    || FileBusinessType.AVATAR.name().equals(file.getBusinessType())
                    || file.getBusinessId() == null) {
                throw new BusinessException("Attachment not found");
            }
            ResourceType parentType = ResourceType.fromFileBusinessType(file.getBusinessType());
            authorization.requireOwnerViewEnabled(actor, parentType, file.getBusinessId());
            var decision = authorization.require(
                    actor, ResourceType.FILE_ATTACHMENT, fileId, AccessPermission.AI_USE);
            OcrTask confirmed = ocrTasks.selectOne(new LambdaQueryWrapper<OcrTask>()
                    .eq(OcrTask::getFileId, fileId)
                    .eq(OcrTask::getTeacherId, file.getTeacherId())
                    .eq(OcrTask::getStatus, "CONFIRMED")
                    .orderByDesc(OcrTask::getId)
                    .last("LIMIT 1"));
            if (confirmed == null || !StringUtils.hasText(confirmed.getRawText())) {
                throw new BusinessException("该附件尚未完成OCR确认，不能用于AI材料整理");
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("fileId", fileId);
            row.put("sourceTeacherId", file.getTeacherId());
            row.put("resourceType", parentType.name());
            row.put("resourceId", file.getBusinessId());
            row.put("fileName", file.getOriginalName());
            row.put("confirmedText", confirmed.getRawText());
            attachmentData.add(row);
            sources.add(new SourceReference(
                    file.getTeacherId(), parentType.name(), file.getBusinessId(), fileId,
                    decision.grant() == null ? null : decision.grant().getId(), null, null));
            audits.record(actor, file.getTeacherId(), "AI_USE", ResourceType.FILE_ATTACHMENT,
                    fileId, fileId, decision.grant(), true, null, "AI material generation");
        }
        data.put("attachments", attachmentData);
    }

    private List<?> load(String module, Long teacherId) {
        return switch (module) {
            case "academicQualifications" -> profileBlockService.listAcademicQualifications(teacherId);
            case "teachingAreas" -> profileBlockService.listTeachingAreas(teacherId);
            case "researchAreas" -> profileBlockService.listResearchAreas(teacherId);
            case "professionalServices" -> profileBlockService.listProfessionalServices(teacherId);
            case "workingExperiences" -> profileBlockService.listWorkingExperiences(teacherId);
            case "projects" -> achievementService.listProjects(teacherId);
            case "teachingCourses" -> achievementService.listTeachingCourses(teacherId);
            case "papers" -> achievementService.listPapers(teacherId);
            case "patents" -> achievementService.listPatents(teacherId);
            case "certificates" -> achievementService.listCertificates(teacherId);
            default -> List.of();
        };
    }

    private boolean matchesFilters(String module, Map<String, Object> row, AiGenerationRequest request) {
        if ("certificates".equals(module)
                && request.competitionCategories() != null
                && !request.competitionCategories().isEmpty()
                && !request.competitionCategories().contains(row.get("competitionCategory"))) {
            return false;
        }
        if (request.startYear() == null && request.endYear() == null) {
            return true;
        }
        Integer year = firstYear(row);
        return year == null
                || (request.startYear() == null || year >= request.startYear())
                && (request.endYear() == null || year <= request.endYear());
    }

    private Integer firstYear(Map<String, Object> row) {
        Object explicit = row.get("publishYear");
        if (explicit instanceof Number number) {
            return number.intValue();
        }
        for (String key : List.of("issueDate", "authorizationDate", "applicationDate", "startDate", "endDate")) {
            Object value = row.get(key);
            if (value instanceof LocalDate date) return date.getYear();
            if (value instanceof String text && text.length() >= 4) {
                try {
                    return Integer.parseInt(text.substring(0, 4));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return null;
    }

    private Set<String> selectedFields(String module, Map<String, List<String>> requested) {
        Set<String> allowed = ALLOWED_FIELDS.get(module);
        if (allowed == null) {
            throw new BusinessException("Invalid AI material module");
        }
        if (requested == null || requested.get(module) == null || requested.get(module).isEmpty()) {
            return allowed;
        }
        Set<String> selected = new LinkedHashSet<>(requested.get(module));
        if (!allowed.containsAll(selected)) {
            throw new BusinessException("Invalid AI material field selection");
        }
        return selected;
    }

    private void validateFilters(AiGenerationRequest request) {
        if (request.startYear() != null && request.endYear() != null
                && request.startYear() > request.endYear()) {
            throw new BusinessException("Start year must not be after end year");
        }
        if (request.competitionCategories() != null
                && request.competitionCategories().stream().anyMatch(value ->
                        !"FIRST_CLASS".equals(value) && !"SECOND_CLASS".equals(value))) {
            throw new BusinessException("Invalid competition category");
        }
        String mode = request.outputMode() == null ? "AI_DRAFT" : request.outputMode();
        if (!Set.of("DETAIL", "COUNT", "AI_DRAFT").contains(mode)) {
            throw new BusinessException("Invalid material output mode");
        }
    }

    private Map<String, Object> sanitize(Object value, Set<String> selected) {
        return sanitizeMap(objectMapper.convertValue(value, new TypeReference<>() {}), selected);
    }

    private Map<String, Object> sanitizeMap(Map<String, Object> value, Set<String> selected) {
        Map<String, Object> result = new LinkedHashMap<>(value);
        result.keySet().removeIf(key -> INTERNAL_FIELDS.contains(key) || !selected.contains(key));
        result.entrySet().removeIf(entry -> entry.getValue() == null || "".equals(entry.getValue()));
        return result;
    }

    public record SourceReference(
            Long teacherId, String resourceType, Long resourceId, Long fileId, Long grantId,
            String provider, String modelName) {
        SourceReference withRuntime(AiRuntimeConfig runtime) {
            return new SourceReference(
                    teacherId, resourceType, resourceId, fileId, grantId,
                    runtime.providerName(), runtime.modelName());
        }
    }

    public record CollectionResult(Map<String, Object> data, List<SourceReference> sources) {
    }
}
