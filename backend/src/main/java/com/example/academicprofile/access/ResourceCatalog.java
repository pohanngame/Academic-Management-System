package com.example.academicprofile.access;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.util.List;
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
import com.example.academicprofile.file.FileMetadata;
import com.example.academicprofile.file.FileMetadataMapper;
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

@Component
public class ResourceCatalog {
    private final AcademicQualificationMapper qualifications;
    private final TeachingAreaMapper teachingAreas;
    private final ResearchAreaMapper researchAreas;
    private final ProfessionalServiceMapper services;
    private final WorkingExperienceMapper experiences;
    private final ProjectMapper projects;
    private final TeachingCourseMapper courses;
    private final PaperMapper papers;
    private final PatentMapper patents;
    private final CertificateMapper certificates;
    private final FileMetadataMapper files;

    public ResourceCatalog(
            AcademicQualificationMapper qualifications,
            TeachingAreaMapper teachingAreas,
            ResearchAreaMapper researchAreas,
            ProfessionalServiceMapper services,
            WorkingExperienceMapper experiences,
            ProjectMapper projects,
            TeachingCourseMapper courses,
            PaperMapper papers,
            PatentMapper patents,
            CertificateMapper certificates,
            FileMetadataMapper files) {
        this.qualifications = qualifications;
        this.teachingAreas = teachingAreas;
        this.researchAreas = researchAreas;
        this.services = services;
        this.experiences = experiences;
        this.projects = projects;
        this.courses = courses;
        this.papers = papers;
        this.patents = patents;
        this.certificates = certificates;
        this.files = files;
    }

    public Long owner(ResourceType type, Long id) {
        if (id == null || id < 1) {
            throw notFound();
        }
        Long owner = switch (type) {
            case ACADEMIC_QUALIFICATION -> owner(qualifications.selectById(id));
            case TEACHING_AREA -> owner(teachingAreas.selectById(id));
            case RESEARCH_AREA -> owner(researchAreas.selectById(id));
            case PROFESSIONAL_SERVICE -> owner(services.selectById(id));
            case WORKING_EXPERIENCE -> owner(experiences.selectById(id));
            case PROJECT -> owner(projects.selectById(id));
            case TEACHING_COURSE -> owner(courses.selectById(id));
            case PAPER -> owner(papers.selectById(id));
            case PATENT -> owner(patents.selectById(id));
            case CERTIFICATE -> owner(certificates.selectById(id));
            case FILE_ATTACHMENT -> owner(files.selectById(id));
        };
        if (owner == null) {
            throw notFound();
        }
        return owner;
    }

    public Object get(ResourceType type, Long id) {
        Object value = switch (type) {
            case ACADEMIC_QUALIFICATION -> qualifications.selectById(id);
            case TEACHING_AREA -> teachingAreas.selectById(id);
            case RESEARCH_AREA -> researchAreas.selectById(id);
            case PROFESSIONAL_SERVICE -> services.selectById(id);
            case WORKING_EXPERIENCE -> experiences.selectById(id);
            case PROJECT -> projects.selectById(id);
            case TEACHING_COURSE -> courses.selectById(id);
            case PAPER -> papers.selectById(id);
            case PATENT -> patents.selectById(id);
            case CERTIFICATE -> certificates.selectById(id);
            case FILE_ATTACHMENT -> files.selectById(id);
        };
        if (value == null) throw notFound();
        return value;
    }

    public List<?> list(ResourceType type, Long teacherId) {
        return switch (type) {
            case ACADEMIC_QUALIFICATION -> qualifications.selectList(
                    new LambdaQueryWrapper<AcademicQualification>().eq(AcademicQualification::getTeacherId, teacherId));
            case TEACHING_AREA -> teachingAreas.selectList(
                    new LambdaQueryWrapper<TeachingArea>().eq(TeachingArea::getTeacherId, teacherId));
            case RESEARCH_AREA -> researchAreas.selectList(
                    new LambdaQueryWrapper<ResearchArea>().eq(ResearchArea::getTeacherId, teacherId));
            case PROFESSIONAL_SERVICE -> services.selectList(
                    new LambdaQueryWrapper<ProfessionalService>().eq(ProfessionalService::getTeacherId, teacherId));
            case WORKING_EXPERIENCE -> experiences.selectList(
                    new LambdaQueryWrapper<WorkingExperience>().eq(WorkingExperience::getTeacherId, teacherId));
            case PROJECT -> projects.selectList(
                    new LambdaQueryWrapper<Project>().eq(Project::getTeacherId, teacherId));
            case TEACHING_COURSE -> courses.selectList(
                    new LambdaQueryWrapper<TeachingCourse>().eq(TeachingCourse::getTeacherId, teacherId));
            case PAPER -> papers.selectList(new LambdaQueryWrapper<Paper>().eq(Paper::getTeacherId, teacherId));
            case PATENT -> patents.selectList(new LambdaQueryWrapper<Patent>().eq(Patent::getTeacherId, teacherId));
            case CERTIFICATE -> certificates.selectList(
                    new LambdaQueryWrapper<Certificate>().eq(Certificate::getTeacherId, teacherId));
            case FILE_ATTACHMENT -> files.selectList(
                    new LambdaQueryWrapper<FileMetadata>()
                            .eq(FileMetadata::getTeacherId, teacherId)
                            .ne(FileMetadata::getBusinessType, "AVATAR")
                            .eq(FileMetadata::getDeleted, Boolean.FALSE));
        };
    }

    public List<?> listByIds(ResourceType type, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return switch (type) {
            case ACADEMIC_QUALIFICATION -> qualifications.selectBatchIds(ids);
            case TEACHING_AREA -> teachingAreas.selectBatchIds(ids);
            case RESEARCH_AREA -> researchAreas.selectBatchIds(ids);
            case PROFESSIONAL_SERVICE -> services.selectBatchIds(ids);
            case WORKING_EXPERIENCE -> experiences.selectBatchIds(ids);
            case PROJECT -> projects.selectBatchIds(ids);
            case TEACHING_COURSE -> courses.selectBatchIds(ids);
            case PAPER -> papers.selectBatchIds(ids);
            case PATENT -> patents.selectBatchIds(ids);
            case CERTIFICATE -> certificates.selectBatchIds(ids);
            case FILE_ATTACHMENT -> files.selectBatchIds(ids);
        };
    }

    public String safeDisplayName(ResourceType type, Long id) {
        return safeDisplayName(type, get(type, id));
    }

    public String safeDisplayName(ResourceType type, Object value) {
        String name = switch (type) {
            case ACADEMIC_QUALIFICATION -> ((AcademicQualification) value).getInstitution();
            case TEACHING_AREA -> ((TeachingArea) value).getName();
            case RESEARCH_AREA -> ((ResearchArea) value).getName();
            case PROFESSIONAL_SERVICE -> ((ProfessionalService) value).getTitle();
            case WORKING_EXPERIENCE -> ((WorkingExperience) value).getOrganization();
            case PROJECT -> ((Project) value).getProjectName();
            case TEACHING_COURSE -> ((TeachingCourse) value).getCourseName();
            case PAPER -> ((Paper) value).getTitle();
            case PATENT -> ((Patent) value).getPatentName();
            case CERTIFICATE -> ((Certificate) value).getCertificateName();
            case FILE_ATTACHMENT -> ((FileMetadata) value).getOriginalName();
        };
        return name == null || name.isBlank() ? type.name() + " #" + idOf(value) : name;
    }

    public Long idOf(Object value) {
        if (value instanceof AcademicQualification item) return item.getId();
        if (value instanceof TeachingArea item) return item.getId();
        if (value instanceof ResearchArea item) return item.getId();
        if (value instanceof ProfessionalService item) return item.getId();
        if (value instanceof WorkingExperience item) return item.getId();
        if (value instanceof Project item) return item.getId();
        if (value instanceof TeachingCourse item) return item.getId();
        if (value instanceof Paper item) return item.getId();
        if (value instanceof Patent item) return item.getId();
        if (value instanceof Certificate item) return item.getId();
        if (value instanceof FileMetadata item) return item.getId();
        return null;
    }

    private Long owner(Object value) {
        if (value instanceof AcademicQualification item) return item.getTeacherId();
        if (value instanceof TeachingArea item) return item.getTeacherId();
        if (value instanceof ResearchArea item) return item.getTeacherId();
        if (value instanceof ProfessionalService item) return item.getTeacherId();
        if (value instanceof WorkingExperience item) return item.getTeacherId();
        if (value instanceof Project item) return item.getTeacherId();
        if (value instanceof TeachingCourse item) return item.getTeacherId();
        if (value instanceof Paper item) return item.getTeacherId();
        if (value instanceof Patent item) return item.getTeacherId();
        if (value instanceof Certificate item) return item.getTeacherId();
        if (value instanceof FileMetadata item
                && !"AVATAR".equals(item.getBusinessType())
                && !Boolean.TRUE.equals(item.getDeleted())) return item.getTeacherId();
        return null;
    }

    private BusinessException notFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "Resource not found");
    }
}
