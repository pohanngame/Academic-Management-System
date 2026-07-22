package com.example.academicprofile.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.example.academicprofile.achievement.AchievementService;
import com.example.academicprofile.profileblock.ProfileBlockService;
import com.example.academicprofile.teacher.TeacherProfileResponse;
import com.example.academicprofile.teacher.TeacherProfileService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class AiDataCollector {

    private static final Set<String> INTERNAL_FIELDS = Set.of(
            "id",
            "userId",
            "teacherId",
            "avatarFileId",
            "createdAt",
            "updatedAt",
            "sortOrder",
            "isPublic",
            "publicEnabled",
            "publicSlug",
            "fieldVisibilityConfig");

    private final TeacherProfileService teacherProfileService;
    private final ProfileBlockService profileBlockService;
    private final AchievementService achievementService;
    private final ObjectMapper objectMapper;

    public AiDataCollector(
            TeacherProfileService teacherProfileService,
            ProfileBlockService profileBlockService,
            AchievementService achievementService,
            ObjectMapper objectMapper) {
        this.teacherProfileService = teacherProfileService;
        this.profileBlockService = profileBlockService;
        this.achievementService = achievementService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> collect(Long teacherId, List<String> modules) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String module : modules) {
            switch (module) {
                case "teacherProfile" -> result.put(module,
                        sanitize(TeacherProfileResponse.from(teacherProfileService.getByTeacherId(teacherId))));
                case "academicQualifications" -> result.put(module,
                        sanitizeList(profileBlockService.listAcademicQualifications(teacherId)));
                case "teachingAreas" -> result.put(module, sanitizeList(profileBlockService.listTeachingAreas(teacherId)));
                case "researchAreas" -> result.put(module, sanitizeList(profileBlockService.listResearchAreas(teacherId)));
                case "professionalServices" -> result.put(module,
                        sanitizeList(profileBlockService.listProfessionalServices(teacherId)));
                case "workingExperiences" -> result.put(module,
                        sanitizeList(profileBlockService.listWorkingExperiences(teacherId)));
                case "projects" -> result.put(module, sanitizeList(achievementService.listProjects(teacherId)));
                case "teachingCourses" -> result.put(module, sanitizeList(achievementService.listTeachingCourses(teacherId)));
                case "papers" -> result.put(module, sanitizeList(achievementService.listPapers(teacherId)));
                case "patents" -> result.put(module, sanitizeList(achievementService.listPatents(teacherId)));
                case "certificates" -> result.put(module, sanitizeList(achievementService.listCertificates(teacherId)));
                default -> {
                }
            }
        }
        return result;
    }

    private List<Map<String, Object>> sanitizeList(List<?> rows) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object row : rows) {
            result.add(sanitize(row));
        }
        return result;
    }

    private Map<String, Object> sanitize(Object value) {
        Map<String, Object> map = objectMapper.convertValue(value, new TypeReference<>() {
        });
        map.keySet().removeIf(INTERNAL_FIELDS::contains);
        map.entrySet().removeIf(entry -> entry.getValue() == null || "".equals(entry.getValue()));
        return map;
    }
}
