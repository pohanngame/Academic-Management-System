package com.example.academicprofile.organization;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.academicprofile.admin.AdminAuditService;
import com.example.academicprofile.access.AccessDtos.AccessCapabilitiesResponse;
import com.example.academicprofile.common.exception.BusinessException;
import com.example.academicprofile.organization.OrganizationDtos.MajorRequest;
import com.example.academicprofile.organization.OrganizationDtos.RoleAssignmentRequest;
import com.example.academicprofile.organization.OrganizationDtos.RoleAssignmentResponse;
import com.example.academicprofile.organization.OrganizationDtos.TeacherOrganizationRequest;
import com.example.academicprofile.organization.OrganizationDtos.TeacherOrganizationResponse;
import com.example.academicprofile.organization.OrganizationDtos.TeacherOption;
import com.example.academicprofile.security.CurrentUser;
import com.example.academicprofile.teacher.TeacherProfileService;
import com.example.academicprofile.user.UserAccount;
import com.example.academicprofile.user.UserAccountService;
import com.example.academicprofile.user.UserRole;

@Service
public class OrganizationService {
    private final MajorMapper majors;
    private final TeacherMajorMapper teacherMajors;
    private final AcademicRoleAssignmentMapper assignments;
    private final TeacherProfileService teachers;
    private final UserAccountService users;
    private final AdminAuditService audit;

    public OrganizationService(
            MajorMapper majors,
            TeacherMajorMapper teacherMajors,
            AcademicRoleAssignmentMapper assignments,
            TeacherProfileService teachers,
            UserAccountService users,
            AdminAuditService audit) {
        this.majors = majors;
        this.teacherMajors = teacherMajors;
        this.assignments = assignments;
        this.teachers = teachers;
        this.users = users;
        this.audit = audit;
    }

    @Transactional
    public void assignDefaultRole(Long userId) {
        AcademicRoleAssignment assignment = new AcademicRoleAssignment();
        assignment.setUserId(userId);
        assignment.setRoleCode(AcademicRole.FULL_TIME_TEACHER.name());
        assignments.insert(assignment);
    }

    public List<Major> listMajors(boolean includeInactive) {
        return majors.selectList(new LambdaQueryWrapper<Major>()
                .eq(!includeInactive, Major::getActive, Boolean.TRUE)
                .orderByAsc(Major::getName));
    }

    public List<TeacherOption> listTeachers() {
        return teachers.listAll().stream()
                .map(profile -> new TeacherOption(profile.getId(), profile.getDisplayName()))
                .toList();
    }

    @Transactional
    public Major createMajor(CurrentUser operator, MajorRequest request) {
        Major major = new Major();
        apply(major, request);
        majors.insert(major);
        audit.record(operator.userId(), "MAJOR_CREATE", null, true, "Major created: id=" + major.getId());
        return major;
    }

    @Transactional
    public Major updateMajor(CurrentUser operator, Long id, MajorRequest request) {
        Major major = requireMajor(id, false);
        apply(major, request);
        majors.updateById(major);
        audit.record(operator.userId(), "MAJOR_UPDATE", null, true, "Major updated: id=" + id);
        return major;
    }

    public TeacherOrganizationResponse getTeacherOrganization(Long teacherId) {
        var profile = teachers.getByTeacherId(teacherId);
        TeacherMajor membership = teacherMajors.selectById(teacherId);
        List<RoleAssignmentResponse> roles = assignments.selectList(
                        new LambdaQueryWrapper<AcademicRoleAssignment>()
                                .eq(AcademicRoleAssignment::getUserId, profile.getUserId())
                                .orderByAsc(AcademicRoleAssignment::getRoleCode))
                .stream()
                .map(item -> new RoleAssignmentResponse(item.getRoleCode(), item.getMajorId()))
                .toList();
        return new TeacherOrganizationResponse(
                teacherId,
                membership == null ? null : membership.getMajorId(),
                roles);
    }

    @Transactional
    public TeacherOrganizationResponse updateTeacherOrganization(
            CurrentUser operator, Long teacherId, TeacherOrganizationRequest request) {
        var profile = teachers.getByTeacherId(teacherId);
        UserAccount target = users.findById(profile.getUserId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "User not found"));
        if (UserRole.require(target.getRole()) == UserRole.SUPER_ADMIN) {
            // Assignments may exist for administration display, but never grant private-data access.
        }
        if (request.majorId() == null) {
            teacherMajors.deleteById(teacherId);
        } else {
            requireMajor(request.majorId(), true);
            TeacherMajor membership = new TeacherMajor();
            membership.setTeacherId(teacherId);
            membership.setMajorId(request.majorId());
            teacherMajors.deleteById(teacherId);
            teacherMajors.insert(membership);
        }

        List<AcademicRoleAssignment> desired = validateAssignments(target.getId(), request.roles());
        assignments.delete(new LambdaQueryWrapper<AcademicRoleAssignment>()
                .eq(AcademicRoleAssignment::getUserId, target.getId()));
        desired.forEach(assignments::insert);
        target.setAuthVersion(target.getAuthVersion() == null ? 1L : target.getAuthVersion() + 1L);
        users.update(target);
        audit.record(operator.userId(), "ACADEMIC_ROLE_CHANGE", target.getId(), true,
                "Academic roles and major updated");
        return getTeacherOrganization(teacherId);
    }

    public List<AcademicRoleAssignment> rolesForUser(Long userId) {
        return assignments.selectList(new LambdaQueryWrapper<AcademicRoleAssignment>()
                .eq(AcademicRoleAssignment::getUserId, userId));
    }

    public AccessCapabilitiesResponse accessCapabilities(CurrentUser actor) {
        if (UserRole.SUPER_ADMIN.name().equals(actor.role())) {
            throw new BusinessException(HttpStatus.FORBIDDEN,
                    "SUPER_ADMIN cannot browse private teacher data");
        }
        List<AcademicRoleAssignment> roles = rolesForUser(actor.userId());
        Long currentMajorId = majorForTeacher(actor.teacherId());
        Major currentMajor = currentMajorId == null ? null : majors.selectById(currentMajorId);
        List<Long> managedMajorIds = roles.stream()
                .filter(item -> AcademicRole.MAJOR_LEADER.name().equals(item.getRoleCode()))
                .map(AcademicRoleAssignment::getMajorId)
                .filter(this::isMajorActive)
                .distinct()
                .toList();
        boolean collegeWide = roles.stream().map(AcademicRoleAssignment::getRoleCode)
                .anyMatch(role -> AcademicRole.VICE_DEAN.name().equals(role)
                        || AcademicRole.DEAN.name().equals(role)
                        || AcademicRole.PARTY_SECRETARY.name().equals(role));
        boolean canManage = collegeWide || !managedMajorIds.isEmpty();
        return new AccessCapabilitiesResponse(
                roles.stream().map(AcademicRoleAssignment::getRoleCode).distinct().sorted().toList(),
                currentMajorId,
                currentMajor == null ? null : currentMajor.getName(),
                managedMajorIds,
                canManage,
                canManage);
    }

    public Long majorForTeacher(Long teacherId) {
        TeacherMajor membership = teacherMajors.selectById(teacherId);
        return membership == null ? null : membership.getMajorId();
    }

    public Long activeMajorForTeacher(Long teacherId) {
        Long majorId = majorForTeacher(teacherId);
        return isMajorActive(majorId) ? majorId : null;
    }

    public boolean isMajorActive(Long majorId) {
        if (majorId == null) {
            return false;
        }
        Major major = majors.selectById(majorId);
        return major != null && Boolean.TRUE.equals(major.getActive());
    }

    private List<AcademicRoleAssignment> validateAssignments(
            Long userId, List<RoleAssignmentRequest> requested) {
        List<AcademicRoleAssignment> result = new ArrayList<>();
        boolean hasFullTime = false;
        for (RoleAssignmentRequest item : requested) {
            AcademicRole role = AcademicRole.require(item.role());
            Long majorId = item.majorId();
            if (role.requiresMajor()) {
                requireMajor(majorId, true);
            } else if (majorId != null) {
                throw new BusinessException("Only MAJOR_LEADER can be scoped to a major");
            }
            if (role == AcademicRole.FULL_TIME_TEACHER) {
                hasFullTime = true;
            }
            boolean duplicate = result.stream().anyMatch(existing ->
                    existing.getRoleCode().equals(role.name())
                            && java.util.Objects.equals(existing.getMajorId(), majorId));
            if (duplicate) {
                throw new BusinessException("Duplicate academic role assignment");
            }
            AcademicRoleAssignment assignment = new AcademicRoleAssignment();
            assignment.setUserId(userId);
            assignment.setRoleCode(role.name());
            assignment.setMajorId(majorId);
            result.add(assignment);
        }
        if (!hasFullTime) {
            throw new BusinessException("FULL_TIME_TEACHER role is required");
        }
        return result;
    }

    private Major requireMajor(Long id, boolean activeOnly) {
        if (id == null || id < 1) {
            throw new BusinessException("Invalid major");
        }
        Major major = majors.selectById(id);
        if (major == null || (activeOnly && !Boolean.TRUE.equals(major.getActive()))) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Major not found");
        }
        return major;
    }

    private void apply(Major major, MajorRequest request) {
        major.setCode(request.code().trim());
        major.setName(request.name().trim());
        major.setActive(request.active() == null ? Boolean.TRUE : request.active());
        if (!StringUtils.hasText(major.getCode()) || !StringUtils.hasText(major.getName())) {
            throw new BusinessException("Major code and name are required");
        }
    }
}
