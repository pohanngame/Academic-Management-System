package com.example.academicprofile.achievement;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.academicprofile.common.exception.BusinessException;

@Service
public class AchievementService {

    private final ProjectMapper projectMapper;
    private final TeachingCourseMapper teachingCourseMapper;
    private final PaperMapper paperMapper;
    private final PatentMapper patentMapper;
    private final CertificateMapper certificateMapper;

    public AchievementService(
            ProjectMapper projectMapper,
            TeachingCourseMapper teachingCourseMapper,
            PaperMapper paperMapper,
            PatentMapper patentMapper,
            CertificateMapper certificateMapper) {
        this.projectMapper = projectMapper;
        this.teachingCourseMapper = teachingCourseMapper;
        this.paperMapper = paperMapper;
        this.patentMapper = patentMapper;
        this.certificateMapper = certificateMapper;
    }

    public List<Project> listProjects(Long teacherId) {
        return projectMapper.selectList(new LambdaQueryWrapper<Project>()
                .eq(Project::getTeacherId, teacherId)
                .orderByAsc(Project::getSortOrder)
                .orderByDesc(Project::getId));
    }

    @Transactional
    public Project createProject(Long teacherId, ProjectRequest request) {
        Project item = new Project();
        item.setTeacherId(teacherId);
        applyProject(item, request);
        projectMapper.insert(item);
        return item;
    }

    @Transactional
    public Project updateProject(Long teacherId, Long id, ProjectRequest request) {
        Project item = getOwned(projectMapper, teacherId, id, Project::getTeacherId);
        applyProject(item, request);
        projectMapper.updateById(item);
        return item;
    }

    @Transactional
    public void deleteProject(Long teacherId, Long id) {
        deleteOwned(projectMapper, teacherId, id, Project::getTeacherId);
    }

    public List<TeachingCourse> listTeachingCourses(Long teacherId) {
        return teachingCourseMapper.selectList(new LambdaQueryWrapper<TeachingCourse>()
                .eq(TeachingCourse::getTeacherId, teacherId)
                .orderByAsc(TeachingCourse::getSortOrder)
                .orderByDesc(TeachingCourse::getId));
    }

    @Transactional
    public TeachingCourse createTeachingCourse(Long teacherId, TeachingCourseRequest request) {
        TeachingCourse item = new TeachingCourse();
        item.setTeacherId(teacherId);
        applyTeachingCourse(item, request);
        teachingCourseMapper.insert(item);
        return item;
    }

    @Transactional
    public TeachingCourse updateTeachingCourse(Long teacherId, Long id, TeachingCourseRequest request) {
        TeachingCourse item = getOwned(teachingCourseMapper, teacherId, id, TeachingCourse::getTeacherId);
        applyTeachingCourse(item, request);
        teachingCourseMapper.updateById(item);
        return item;
    }

    @Transactional
    public void deleteTeachingCourse(Long teacherId, Long id) {
        deleteOwned(teachingCourseMapper, teacherId, id, TeachingCourse::getTeacherId);
    }

    public List<Paper> listPapers(Long teacherId) {
        return paperMapper.selectList(new LambdaQueryWrapper<Paper>()
                .eq(Paper::getTeacherId, teacherId)
                .orderByAsc(Paper::getSortOrder)
                .orderByDesc(Paper::getId));
    }

    @Transactional
    public Paper createPaper(Long teacherId, PaperRequest request) {
        Paper item = new Paper();
        item.setTeacherId(teacherId);
        applyPaper(item, request);
        paperMapper.insert(item);
        return item;
    }

    @Transactional
    public Paper updatePaper(Long teacherId, Long id, PaperRequest request) {
        Paper item = getOwned(paperMapper, teacherId, id, Paper::getTeacherId);
        applyPaper(item, request);
        paperMapper.updateById(item);
        return item;
    }

    @Transactional
    public void deletePaper(Long teacherId, Long id) {
        deleteOwned(paperMapper, teacherId, id, Paper::getTeacherId);
    }

    public List<Patent> listPatents(Long teacherId) {
        return patentMapper.selectList(new LambdaQueryWrapper<Patent>()
                .eq(Patent::getTeacherId, teacherId)
                .orderByAsc(Patent::getSortOrder)
                .orderByDesc(Patent::getId));
    }

    @Transactional
    public Patent createPatent(Long teacherId, PatentRequest request) {
        Patent item = new Patent();
        item.setTeacherId(teacherId);
        applyPatent(item, request);
        patentMapper.insert(item);
        return item;
    }

    @Transactional
    public Patent updatePatent(Long teacherId, Long id, PatentRequest request) {
        Patent item = getOwned(patentMapper, teacherId, id, Patent::getTeacherId);
        applyPatent(item, request);
        patentMapper.updateById(item);
        return item;
    }

    @Transactional
    public void deletePatent(Long teacherId, Long id) {
        deleteOwned(patentMapper, teacherId, id, Patent::getTeacherId);
    }

    public List<Certificate> listCertificates(Long teacherId) {
        return certificateMapper.selectList(new LambdaQueryWrapper<Certificate>()
                .eq(Certificate::getTeacherId, teacherId)
                .orderByAsc(Certificate::getSortOrder)
                .orderByDesc(Certificate::getId));
    }

    @Transactional
    public Certificate createCertificate(Long teacherId, CertificateRequest request) {
        Certificate item = new Certificate();
        item.setTeacherId(teacherId);
        applyCertificate(item, request);
        certificateMapper.insert(item);
        return item;
    }

    @Transactional
    public Certificate updateCertificate(Long teacherId, Long id, CertificateRequest request) {
        Certificate item = getOwned(certificateMapper, teacherId, id, Certificate::getTeacherId);
        applyCertificate(item, request);
        certificateMapper.updateById(item);
        return item;
    }

    @Transactional
    public void deleteCertificate(Long teacherId, Long id) {
        deleteOwned(certificateMapper, teacherId, id, Certificate::getTeacherId);
    }

    private void applyProject(Project item, ProjectRequest request) {
        item.setProjectName(request.projectName());
        item.setSource(blankToNull(request.source()));
        item.setRole(blankToNull(request.role()));
        item.setStartDate(request.startDate());
        item.setEndDate(request.endDate());
        item.setFundingAmount(request.fundingAmount());
        item.setStatus(blankToNull(request.status()));
        item.setDescription(blankToNull(request.description()));
        item.setSortOrder(defaultSortOrder(request.sortOrder()));
        item.setIsPublic(defaultPublic(request.isPublic()));
    }

    private void applyTeachingCourse(TeachingCourse item, TeachingCourseRequest request) {
        item.setCourseName(request.courseName());
        item.setSemester(blankToNull(request.semester()));
        item.setClassName(blankToNull(request.className()));
        item.setTeachingTarget(blankToNull(request.teachingTarget()));
        item.setHours(request.hours());
        item.setDescription(blankToNull(request.description()));
        item.setSortOrder(defaultSortOrder(request.sortOrder()));
        item.setIsPublic(defaultPublic(request.isPublic()));
    }

    private void applyPaper(Paper item, PaperRequest request) {
        item.setTitle(request.title());
        item.setAuthors(blankToNull(request.authors()));
        item.setPublicationName(blankToNull(request.publicationName()));
        item.setPublicationType(blankToNull(request.publicationType()));
        item.setPublishYear(request.publishYear());
        item.setDoi(blankToNull(request.doi()));
        item.setVolume(blankToNull(request.volume()));
        item.setIssue(blankToNull(request.issue()));
        item.setPages(blankToNull(request.pages()));
        item.setPublisher(blankToNull(request.publisher()));
        item.setUrl(blankToNull(request.url()));
        item.setAbstractText(blankToNull(request.abstractText()));
        item.setKeywords(blankToNull(request.keywords()));
        item.setSortOrder(defaultSortOrder(request.sortOrder()));
        item.setIsPublic(defaultPublic(request.isPublic()));
    }

    private void applyPatent(Patent item, PatentRequest request) {
        item.setPatentName(request.patentName());
        item.setPatentNumber(blankToNull(request.patentNumber()));
        item.setPatentType(blankToNull(request.patentType()));
        item.setStatus(blankToNull(request.status()));
        item.setApplicationDate(request.applicationDate());
        item.setAuthorizationDate(request.authorizationDate());
        item.setInventors(blankToNull(request.inventors()));
        item.setDescription(blankToNull(request.description()));
        item.setSortOrder(defaultSortOrder(request.sortOrder()));
        item.setIsPublic(defaultPublic(request.isPublic()));
    }

    private void applyCertificate(Certificate item, CertificateRequest request) {
        item.setCertificateName(request.certificateName());
        item.setCertificateType(blankToNull(request.certificateType()));
        item.setIssuingAuthority(blankToNull(request.issuingAuthority()));
        item.setIssueDate(request.issueDate());
        item.setDescription(blankToNull(request.description()));
        item.setSortOrder(defaultSortOrder(request.sortOrder()));
        item.setIsPublic(defaultPublic(request.isPublic()));
    }

    private <T> T getOwned(BaseMapper<T> mapper, Long teacherId, Long id, TeacherIdReader<T> teacherIdReader) {
        T item = mapper.selectById(id);
        if (item == null || !teacherId.equals(teacherIdReader.teacherId(item))) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Record not found");
        }
        return item;
    }

    private <T> void deleteOwned(BaseMapper<T> mapper, Long teacherId, Long id, TeacherIdReader<T> teacherIdReader) {
        T item = getOwned(mapper, teacherId, id, teacherIdReader);
        mapper.deleteById(item);
    }

    private Integer defaultSortOrder(Integer sortOrder) {
        return sortOrder == null ? 0 : sortOrder;
    }

    private Boolean defaultPublic(Boolean isPublic) {
        return isPublic == null ? Boolean.TRUE : isPublic;
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    @FunctionalInterface
    private interface TeacherIdReader<T> {
        Long teacherId(T item);
    }
}
