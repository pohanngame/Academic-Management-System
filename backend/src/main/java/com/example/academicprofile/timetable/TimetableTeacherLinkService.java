package com.example.academicprofile.timetable;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.academicprofile.admin.AdminAuditService;
import com.example.academicprofile.common.exception.BusinessException;
import com.example.academicprofile.organization.OrganizationDtos.TeacherOption;
import com.example.academicprofile.teacher.TeacherProfile;
import com.example.academicprofile.teacher.TeacherProfileService;
import com.example.academicprofile.timetable.TimetableDtos.TeacherLinkRequest;
import com.example.academicprofile.timetable.TimetableDtos.TeacherLinkResponse;

@Service
public class TimetableTeacherLinkService {
    private final TimetableTeacherLinkMapper links;
    private final TeacherProfileService teachers;
    private final AdminAuditService audit;

    public TimetableTeacherLinkService(
            TimetableTeacherLinkMapper links,
            TeacherProfileService teachers,
            AdminAuditService audit) {
        this.links = links;
        this.teachers = teachers;
        this.audit = audit;
    }

    public List<TeacherLinkResponse> list() {
        List<TimetableTeacherLink> rows = links.selectList(
                new LambdaQueryWrapper<TimetableTeacherLink>()
                        .orderByAsc(TimetableTeacherLink::getSourceName));
        List<TeacherLinkResponse> responses = new ArrayList<>(rows.size());
        for (TimetableTeacherLink row : rows) {
            TeacherProfile profile = teachers.getByTeacherId(row.getTeacherId());
            responses.add(new TeacherLinkResponse(
                    row.getId(),
                    row.getSourceName(),
                    row.getTeacherId(),
                    profile.getDisplayName()));
        }
        return responses;
    }

    @Transactional
    public TeacherLinkResponse upsert(Long operatorUserId, TeacherLinkRequest request) {
        String sourceName = request.sourceName().trim();
        if (sourceName.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "sourceName must not be blank");
        }
        Long teacherId = request.teacherId();
        // 教师必须存在：teachers.getByTeacherId 在缺失时抛 404。
        TeacherProfile profile = teachers.getByTeacherId(teacherId);

        TimetableTeacherLink byTeacher = links.selectOne(
                new LambdaQueryWrapper<TimetableTeacherLink>()
                        .eq(TimetableTeacherLink::getTeacherId, teacherId));
        if (byTeacher != null && !byTeacher.getSourceName().equals(sourceName)) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Teacher is already linked to another timetable name: "
                            + byTeacher.getSourceName());
        }

        TimetableTeacherLink existing = links.selectOne(
                new LambdaQueryWrapper<TimetableTeacherLink>()
                        .eq(TimetableTeacherLink::getSourceName, sourceName));
        if (existing == null) {
            TimetableTeacherLink created = new TimetableTeacherLink();
            created.setSourceName(sourceName);
            created.setTeacherId(teacherId);
            links.insert(created);
            audit.record(operatorUserId, "TIMETABLE_TEACHER_LINK_CREATE", null, true,
                    "Timetable name mapped: " + sourceName);
            return new TeacherLinkResponse(
                    created.getId(), sourceName, teacherId, profile.getDisplayName());
        }

        existing.setTeacherId(teacherId);
        links.updateById(existing);
        audit.record(operatorUserId, "TIMETABLE_TEACHER_LINK_UPDATE", null, true,
                "Timetable name remapped: " + sourceName);
        return new TeacherLinkResponse(
                existing.getId(), sourceName, teacherId, profile.getDisplayName());
    }

    @Transactional
    public void delete(Long operatorUserId, String sourceName) {
        if (sourceName == null || sourceName.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "sourceName must not be blank");
        }
        String normalized = sourceName.trim();
        TimetableTeacherLink existing = links.selectOne(
                new LambdaQueryWrapper<TimetableTeacherLink>()
                        .eq(TimetableTeacherLink::getSourceName, normalized));
        if (existing == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Timetable name mapping not found");
        }
        links.deleteById(existing.getId());
        audit.record(operatorUserId, "TIMETABLE_TEACHER_LINK_DELETE", null, true,
                "Timetable name mapping removed: " + normalized);
    }

    /** 供后续同步任务复用：按教务显示名查已映射教师。 */
    public List<TeacherOption> mappedTeachers() {
        return list().stream()
                .map(item -> new TeacherOption(item.teacherId(), item.teacherDisplayName()))
                .toList();
    }
}
