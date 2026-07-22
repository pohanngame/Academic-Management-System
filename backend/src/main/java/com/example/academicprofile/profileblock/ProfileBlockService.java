package com.example.academicprofile.profileblock;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.academicprofile.common.exception.BusinessException;

@Service
public class ProfileBlockService {

    private final AcademicQualificationMapper academicQualificationMapper;
    private final TeachingAreaMapper teachingAreaMapper;
    private final ResearchAreaMapper researchAreaMapper;
    private final ProfessionalServiceMapper professionalServiceMapper;
    private final WorkingExperienceMapper workingExperienceMapper;

    public ProfileBlockService(
            AcademicQualificationMapper academicQualificationMapper,
            TeachingAreaMapper teachingAreaMapper,
            ResearchAreaMapper researchAreaMapper,
            ProfessionalServiceMapper professionalServiceMapper,
            WorkingExperienceMapper workingExperienceMapper) {
        this.academicQualificationMapper = academicQualificationMapper;
        this.teachingAreaMapper = teachingAreaMapper;
        this.researchAreaMapper = researchAreaMapper;
        this.professionalServiceMapper = professionalServiceMapper;
        this.workingExperienceMapper = workingExperienceMapper;
    }

    public List<AcademicQualification> listAcademicQualifications(Long teacherId) {
        return academicQualificationMapper.selectList(new LambdaQueryWrapper<AcademicQualification>()
                .eq(AcademicQualification::getTeacherId, teacherId)
                .orderByAsc(AcademicQualification::getSortOrder)
                .orderByDesc(AcademicQualification::getId));
    }

    public List<TeachingArea> listTeachingAreas(Long teacherId) {
        return teachingAreaMapper.selectList(new LambdaQueryWrapper<TeachingArea>()
                .eq(TeachingArea::getTeacherId, teacherId)
                .orderByAsc(TeachingArea::getSortOrder)
                .orderByDesc(TeachingArea::getId));
    }

    public List<ResearchArea> listResearchAreas(Long teacherId) {
        return researchAreaMapper.selectList(new LambdaQueryWrapper<ResearchArea>()
                .eq(ResearchArea::getTeacherId, teacherId)
                .orderByAsc(ResearchArea::getSortOrder)
                .orderByDesc(ResearchArea::getId));
    }

    public List<ProfessionalService> listProfessionalServices(Long teacherId) {
        return professionalServiceMapper.selectList(new LambdaQueryWrapper<ProfessionalService>()
                .eq(ProfessionalService::getTeacherId, teacherId)
                .orderByAsc(ProfessionalService::getSortOrder)
                .orderByDesc(ProfessionalService::getId));
    }

    public List<WorkingExperience> listWorkingExperiences(Long teacherId) {
        return workingExperienceMapper.selectList(new LambdaQueryWrapper<WorkingExperience>()
                .eq(WorkingExperience::getTeacherId, teacherId)
                .orderByAsc(WorkingExperience::getSortOrder)
                .orderByDesc(WorkingExperience::getId));
    }

    @Transactional
    public AcademicQualification createAcademicQualification(Long teacherId, AcademicQualificationRequest request) {
        AcademicQualification item = new AcademicQualification();
        item.setTeacherId(teacherId);
        applyAcademicQualification(item, request);
        academicQualificationMapper.insert(item);
        return item;
    }

    @Transactional
    public AcademicQualification updateAcademicQualification(Long teacherId, Long id, AcademicQualificationRequest request) {
        AcademicQualification item = getOwned(academicQualificationMapper, teacherId, id, AcademicQualification::getTeacherId);
        applyAcademicQualification(item, request);
        academicQualificationMapper.updateById(item);
        return item;
    }

    @Transactional
    public void deleteAcademicQualification(Long teacherId, Long id) {
        deleteOwned(academicQualificationMapper, teacherId, id, AcademicQualification::getTeacherId);
    }

    @Transactional
    public TeachingArea createTeachingArea(Long teacherId, AreaBlockRequest request) {
        TeachingArea item = new TeachingArea();
        item.setTeacherId(teacherId);
        applyArea(item, request);
        teachingAreaMapper.insert(item);
        return item;
    }

    @Transactional
    public TeachingArea updateTeachingArea(Long teacherId, Long id, AreaBlockRequest request) {
        TeachingArea item = getOwned(teachingAreaMapper, teacherId, id, TeachingArea::getTeacherId);
        applyArea(item, request);
        teachingAreaMapper.updateById(item);
        return item;
    }

    @Transactional
    public void deleteTeachingArea(Long teacherId, Long id) {
        deleteOwned(teachingAreaMapper, teacherId, id, TeachingArea::getTeacherId);
    }

    @Transactional
    public ResearchArea createResearchArea(Long teacherId, AreaBlockRequest request) {
        ResearchArea item = new ResearchArea();
        item.setTeacherId(teacherId);
        applyArea(item, request);
        researchAreaMapper.insert(item);
        return item;
    }

    @Transactional
    public ResearchArea updateResearchArea(Long teacherId, Long id, AreaBlockRequest request) {
        ResearchArea item = getOwned(researchAreaMapper, teacherId, id, ResearchArea::getTeacherId);
        applyArea(item, request);
        researchAreaMapper.updateById(item);
        return item;
    }

    @Transactional
    public void deleteResearchArea(Long teacherId, Long id) {
        deleteOwned(researchAreaMapper, teacherId, id, ResearchArea::getTeacherId);
    }

    @Transactional
    public ProfessionalService createProfessionalService(Long teacherId, ProfessionalServiceRequest request) {
        ProfessionalService item = new ProfessionalService();
        item.setTeacherId(teacherId);
        applyProfessionalService(item, request);
        professionalServiceMapper.insert(item);
        return item;
    }

    @Transactional
    public ProfessionalService updateProfessionalService(Long teacherId, Long id, ProfessionalServiceRequest request) {
        ProfessionalService item = getOwned(professionalServiceMapper, teacherId, id, ProfessionalService::getTeacherId);
        applyProfessionalService(item, request);
        professionalServiceMapper.updateById(item);
        return item;
    }

    @Transactional
    public void deleteProfessionalService(Long teacherId, Long id) {
        deleteOwned(professionalServiceMapper, teacherId, id, ProfessionalService::getTeacherId);
    }

    @Transactional
    public WorkingExperience createWorkingExperience(Long teacherId, WorkingExperienceRequest request) {
        WorkingExperience item = new WorkingExperience();
        item.setTeacherId(teacherId);
        applyWorkingExperience(item, request);
        workingExperienceMapper.insert(item);
        return item;
    }

    @Transactional
    public WorkingExperience updateWorkingExperience(Long teacherId, Long id, WorkingExperienceRequest request) {
        WorkingExperience item = getOwned(workingExperienceMapper, teacherId, id, WorkingExperience::getTeacherId);
        applyWorkingExperience(item, request);
        workingExperienceMapper.updateById(item);
        return item;
    }

    @Transactional
    public void deleteWorkingExperience(Long teacherId, Long id) {
        deleteOwned(workingExperienceMapper, teacherId, id, WorkingExperience::getTeacherId);
    }

    private void applyAcademicQualification(AcademicQualification item, AcademicQualificationRequest request) {
        item.setDegree(request.degree());
        item.setInstitution(blankToNull(request.institution()));
        item.setMajor(blankToNull(request.major()));
        item.setStartDate(request.startDate());
        item.setEndDate(request.endDate());
        item.setDescription(blankToNull(request.description()));
        item.setSortOrder(defaultSortOrder(request.sortOrder()));
        item.setIsPublic(defaultPublic(request.isPublic()));
    }

    private void applyArea(TeachingArea item, AreaBlockRequest request) {
        item.setName(request.name());
        item.setDescription(blankToNull(request.description()));
        item.setSortOrder(defaultSortOrder(request.sortOrder()));
        item.setIsPublic(defaultPublic(request.isPublic()));
    }

    private void applyArea(ResearchArea item, AreaBlockRequest request) {
        item.setName(request.name());
        item.setDescription(blankToNull(request.description()));
        item.setSortOrder(defaultSortOrder(request.sortOrder()));
        item.setIsPublic(defaultPublic(request.isPublic()));
    }

    private void applyProfessionalService(ProfessionalService item, ProfessionalServiceRequest request) {
        item.setTitle(request.title());
        item.setOrganization(blankToNull(request.organization()));
        item.setRole(blankToNull(request.role()));
        item.setStartDate(request.startDate());
        item.setEndDate(request.endDate());
        item.setDescription(blankToNull(request.description()));
        item.setSortOrder(defaultSortOrder(request.sortOrder()));
        item.setIsPublic(defaultPublic(request.isPublic()));
    }

    private void applyWorkingExperience(WorkingExperience item, WorkingExperienceRequest request) {
        item.setOrganization(request.organization());
        item.setPosition(blankToNull(request.position()));
        item.setStartDate(request.startDate());
        item.setEndDate(request.endDate());
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
