package com.example.academicprofile.publicprofile;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
import com.example.academicprofile.teacher.TeacherProfileMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class PublicProfileService {

    private static final List<String> CONFIGURABLE_PROFILE_FIELDS = List.of(
            "avatarFileId",
            "title",
            "department",
            "phone",
            "office",
            "profileEmail",
            "biography");

    private final TeacherProfileMapper teacherProfileMapper;
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

    public PublicProfileService(
            TeacherProfileMapper teacherProfileMapper,
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
        this.teacherProfileMapper = teacherProfileMapper;
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
    }

    public PublicProfileResponse getBySlug(String slug) {
        if (!StringUtils.hasText(slug)) {
            throw notFound();
        }
        TeacherProfile profile = teacherProfileMapper.selectOne(new LambdaQueryWrapper<TeacherProfile>()
                .eq(TeacherProfile::getPublicSlug, slug)
                .eq(TeacherProfile::getPublicEnabled, Boolean.TRUE));
        if (profile == null) {
            throw notFound();
        }

        Long teacherId = profile.getId();
        return new PublicProfileResponse(
                toProfile(profile),
                listAcademicQualifications(teacherId),
                listTeachingAreas(teacherId),
                listResearchAreas(teacherId),
                listProfessionalServices(teacherId),
                listWorkingExperiences(teacherId),
                listProjects(teacherId),
                listTeachingCourses(teacherId),
                listPapers(teacherId),
                listPatents(teacherId),
                listCertificates(teacherId));
    }

    private PublicProfileResponse.Profile toProfile(TeacherProfile profile) {
        Map<String, Boolean> visibility = visibilityConfig(profile.getFieldVisibilityConfig());
        return new PublicProfileResponse.Profile(
                visible(visibility, "avatarFileId") ? profile.getAvatarFileId() : null,
                profile.getDisplayName(),
                visible(visibility, "title") ? profile.getTitle() : null,
                visible(visibility, "department") ? profile.getDepartment() : null,
                visible(visibility, "phone") ? profile.getPhone() : null,
                visible(visibility, "office") ? profile.getOffice() : null,
                visible(visibility, "profileEmail") ? profile.getProfileEmail() : null,
                visible(visibility, "biography") ? profile.getBiography() : null,
                profile.getPublicSlug());
    }

    private Map<String, Boolean> visibilityConfig(String rawConfig) {
        Map<String, Boolean> visibility = new HashMap<>();
        CONFIGURABLE_PROFILE_FIELDS.forEach(field -> visibility.put(field, Boolean.TRUE));
        if (!StringUtils.hasText(rawConfig)) {
            return visibility;
        }
        try {
            Map<String, Boolean> parsed = objectMapper.readValue(rawConfig, new TypeReference<>() {
            });
            parsed.forEach((key, value) -> {
                if (CONFIGURABLE_PROFILE_FIELDS.contains(key)) {
                    visibility.put(key, Boolean.TRUE.equals(value));
                }
            });
            return visibility;
        } catch (Exception ex) {
            return visibility;
        }
    }

    private boolean visible(Map<String, Boolean> visibility, String field) {
        return Boolean.TRUE.equals(visibility.getOrDefault(field, Boolean.TRUE));
    }

    private List<PublicProfileResponse.AcademicQualificationItem> listAcademicQualifications(Long teacherId) {
        return academicQualificationMapper.selectList(publicQuery(teacherId, AcademicQualification::getTeacherId,
                        AcademicQualification::getIsPublic, AcademicQualification::getSortOrder, AcademicQualification::getId))
                .stream()
                .map(item -> new PublicProfileResponse.AcademicQualificationItem(
                        item.getId(),
                        item.getDegree(),
                        item.getInstitution(),
                        item.getMajor(),
                        item.getStartDate(),
                        item.getEndDate(),
                        item.getDescription()))
                .toList();
    }

    private List<PublicProfileResponse.AreaItem> listTeachingAreas(Long teacherId) {
        return teachingAreaMapper.selectList(publicQuery(teacherId, TeachingArea::getTeacherId,
                        TeachingArea::getIsPublic, TeachingArea::getSortOrder, TeachingArea::getId))
                .stream()
                .map(item -> new PublicProfileResponse.AreaItem(item.getId(), item.getName(), item.getDescription()))
                .toList();
    }

    private List<PublicProfileResponse.AreaItem> listResearchAreas(Long teacherId) {
        return researchAreaMapper.selectList(publicQuery(teacherId, ResearchArea::getTeacherId,
                        ResearchArea::getIsPublic, ResearchArea::getSortOrder, ResearchArea::getId))
                .stream()
                .map(item -> new PublicProfileResponse.AreaItem(item.getId(), item.getName(), item.getDescription()))
                .toList();
    }

    private List<PublicProfileResponse.ProfessionalServiceItem> listProfessionalServices(Long teacherId) {
        return professionalServiceMapper.selectList(publicQuery(teacherId, ProfessionalService::getTeacherId,
                        ProfessionalService::getIsPublic, ProfessionalService::getSortOrder, ProfessionalService::getId))
                .stream()
                .map(item -> new PublicProfileResponse.ProfessionalServiceItem(
                        item.getId(),
                        item.getTitle(),
                        item.getOrganization(),
                        item.getRole(),
                        item.getStartDate(),
                        item.getEndDate(),
                        item.getDescription()))
                .toList();
    }

    private List<PublicProfileResponse.WorkingExperienceItem> listWorkingExperiences(Long teacherId) {
        return workingExperienceMapper.selectList(publicQuery(teacherId, WorkingExperience::getTeacherId,
                        WorkingExperience::getIsPublic, WorkingExperience::getSortOrder, WorkingExperience::getId))
                .stream()
                .map(item -> new PublicProfileResponse.WorkingExperienceItem(
                        item.getId(),
                        item.getOrganization(),
                        item.getPosition(),
                        item.getStartDate(),
                        item.getEndDate(),
                        item.getDescription()))
                .toList();
    }

    private List<PublicProfileResponse.ProjectItem> listProjects(Long teacherId) {
        return projectMapper.selectList(publicQuery(teacherId, Project::getTeacherId,
                        Project::getIsPublic, Project::getSortOrder, Project::getId))
                .stream()
                .map(item -> new PublicProfileResponse.ProjectItem(
                        item.getId(),
                        item.getProjectName(),
                        item.getSource(),
                        item.getRole(),
                        item.getStartDate(),
                        item.getEndDate(),
                        item.getFundingAmount(),
                        item.getStatus(),
                        item.getDescription()))
                .toList();
    }

    private List<PublicProfileResponse.TeachingCourseItem> listTeachingCourses(Long teacherId) {
        return teachingCourseMapper.selectList(publicQuery(teacherId, TeachingCourse::getTeacherId,
                        TeachingCourse::getIsPublic, TeachingCourse::getSortOrder, TeachingCourse::getId))
                .stream()
                .map(item -> new PublicProfileResponse.TeachingCourseItem(
                        item.getId(),
                        item.getCourseName(),
                        item.getSemester(),
                        item.getClassName(),
                        item.getTeachingTarget(),
                        item.getHours(),
                        item.getDescription()))
                .toList();
    }

    private List<PublicProfileResponse.PaperItem> listPapers(Long teacherId) {
        return paperMapper.selectList(publicQuery(teacherId, Paper::getTeacherId,
                        Paper::getIsPublic, Paper::getSortOrder, Paper::getId))
                .stream()
                .map(item -> new PublicProfileResponse.PaperItem(
                        item.getId(),
                        item.getTitle(),
                        item.getAuthors(),
                        item.getPublicationName(),
                        item.getPublicationType(),
                        item.getPublishYear(),
                        item.getDoi(),
                        item.getVolume(),
                        item.getIssue(),
                        item.getPages(),
                        item.getPublisher(),
                        item.getUrl(),
                        item.getAbstractText(),
                        item.getKeywords()))
                .toList();
    }

    private List<PublicProfileResponse.PatentItem> listPatents(Long teacherId) {
        return patentMapper.selectList(publicQuery(teacherId, Patent::getTeacherId,
                        Patent::getIsPublic, Patent::getSortOrder, Patent::getId))
                .stream()
                .map(item -> new PublicProfileResponse.PatentItem(
                        item.getId(),
                        item.getPatentName(),
                        item.getPatentNumber(),
                        item.getPatentType(),
                        item.getStatus(),
                        item.getApplicationDate(),
                        item.getAuthorizationDate(),
                        item.getInventors(),
                        item.getDescription()))
                .toList();
    }

    private List<PublicProfileResponse.CertificateItem> listCertificates(Long teacherId) {
        return certificateMapper.selectList(publicQuery(teacherId, Certificate::getTeacherId,
                        Certificate::getIsPublic, Certificate::getSortOrder, Certificate::getId))
                .stream()
                .map(item -> new PublicProfileResponse.CertificateItem(
                        item.getId(),
                        item.getCertificateName(),
                        item.getCertificateType(),
                        item.getIssuingAuthority(),
                        item.getIssueDate(),
                        item.getDescription()))
                .toList();
    }

    private <T> LambdaQueryWrapper<T> publicQuery(
            Long teacherId,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, Long> teacherIdColumn,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, Boolean> publicColumn,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, Integer> sortColumn,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, Long> idColumn) {
        return new LambdaQueryWrapper<T>()
                .eq(teacherIdColumn, teacherId)
                .eq(publicColumn, Boolean.TRUE)
                .orderByAsc(sortColumn)
                .orderByDesc(idColumn);
    }

    private BusinessException notFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "Public profile not found");
    }
}
