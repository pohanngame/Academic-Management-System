package com.example.academicprofile.ai;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.academicprofile.common.exception.BusinessException;
import com.example.academicprofile.organization.Major;
import com.example.academicprofile.organization.MajorMapper;
import com.example.academicprofile.organization.TeacherMajor;
import com.example.academicprofile.organization.TeacherMajorMapper;
import com.example.academicprofile.security.CurrentUser;
import com.example.academicprofile.teacher.TeacherProfile;
import com.example.academicprofile.teacher.TeacherProfileService;
import com.example.academicprofile.user.UserRole;

@Service
public class AiSourceTeacherService {
    private static final int RESULT_LIMIT = 101;

    private final AiSourceTeacherMapper mapper;
    private final TeacherProfileService teachers;
    private final TeacherMajorMapper teacherMajors;
    private final MajorMapper majors;

    public AiSourceTeacherService(
            AiSourceTeacherMapper mapper,
            TeacherProfileService teachers,
            TeacherMajorMapper teacherMajors,
            MajorMapper majors) {
        this.mapper = mapper;
        this.teachers = teachers;
        this.teacherMajors = teacherMajors;
        this.majors = majors;
    }

    public List<AiSourceTeacherResponse> list(CurrentUser actor) {
        if (UserRole.SUPER_ADMIN.name().equals(actor.role())) {
            throw new BusinessException(org.springframework.http.HttpStatus.FORBIDDEN, "无权访问教师 AI 数据");
        }
        TeacherProfile self = teachers.getByTeacherId(actor.teacherId());
        List<AiSourceTeacherResponse> result = new ArrayList<>();
        result.add(new AiSourceTeacherResponse(
                self.getId(), self.getDisplayName(), majorName(self.getId()), true, 0, 0));
        mapper.listGranted(actor.teacherId(), LocalDateTime.now(), RESULT_LIMIT - 1).stream()
                .filter(item -> !actor.teacherId().equals(item.teacherId()))
                .map(item -> new AiSourceTeacherResponse(
                        item.teacherId(), item.displayName(), item.majorName(), false,
                        item.availableResourceCount(), item.availableAttachmentCount()))
                .forEach(result::add);
        return List.copyOf(result);
    }

    private String majorName(Long teacherId) {
        TeacherMajor assignment = teacherMajors.selectOne(new LambdaQueryWrapper<TeacherMajor>()
                .eq(TeacherMajor::getTeacherId, teacherId));
        if (assignment == null) {
            return null;
        }
        Major major = majors.selectById(assignment.getMajorId());
        return major == null ? null : major.getName();
    }
}
