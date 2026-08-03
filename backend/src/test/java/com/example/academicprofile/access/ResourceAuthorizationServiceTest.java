package com.example.academicprofile.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.academicprofile.common.exception.BusinessException;
import com.example.academicprofile.organization.AcademicRoleAssignment;
import com.example.academicprofile.organization.OrganizationService;
import com.example.academicprofile.security.CurrentUser;
import com.example.academicprofile.teacher.TeacherProfile;
import com.example.academicprofile.teacher.TeacherProfileService;

class ResourceAuthorizationServiceTest {
    private ResourceCatalog catalog;
    private ResourceAccessPolicyMapper policies;
    private ResourceAccessGrantMapper grants;
    private OrganizationService organization;
    private TeacherProfileService teachers;
    private ResourceAuthorizationService service;

    @BeforeEach
    void setUp() {
        catalog = mock(ResourceCatalog.class);
        policies = mock(ResourceAccessPolicyMapper.class);
        grants = mock(ResourceAccessGrantMapper.class);
        organization = mock(OrganizationService.class);
        teachers = mock(TeacherProfileService.class);
        service = new ResourceAuthorizationService(catalog, policies, grants, organization, teachers);

        when(catalog.owner(ResourceType.PAPER, 10L)).thenReturn(2L);
        ResourceAccessPolicy policy = new ResourceAccessPolicy();
        policy.setOwnerTeacherId(2L);
        policy.setViewAllowed(true);
        policy.setDownloadAllowed(true);
        policy.setAiUseAllowed(true);
        when(policies.selectOne(any(Wrapper.class))).thenReturn(policy);
        TeacherProfile owner = new TeacherProfile();
        owner.setId(2L);
        owner.setUserId(20L);
        when(teachers.getByTeacherId(2L)).thenReturn(owner);
        when(organization.rolesForUser(20L)).thenReturn(List.of(role("FULL_TIME_TEACHER", null)));
    }

    @Test
    void ownerAlwaysHasAccess() {
        var decision = service.require(teacher(1L, 2L), ResourceType.PAPER, 10L, AccessPermission.AI_USE);
        assertThat(decision.ownerTeacherId()).isEqualTo(2L);
        assertThat(decision.grant()).isNull();
    }

    @Test
    void superAdminNeverGetsPrivateAcademicAccess() {
        assertDenied(service, new CurrentUser(1L, 1L, "admin", "a@example.com", "SUPER_ADMIN", 0L),
                AccessPermission.VIEW);
    }

    @Test
    void ownerViewDisabledOverridesManagementRole() {
        ResourceAccessPolicy policy = new ResourceAccessPolicy();
        policy.setOwnerTeacherId(2L);
        policy.setViewAllowed(false);
        when(policies.selectOne(any(Wrapper.class))).thenReturn(policy);
        when(organization.rolesForUser(10L)).thenReturn(List.of(role("DEAN", null)));

        assertDenied(service, teacher(10L, 1L), AccessPermission.VIEW);
    }

    @Test
    void majorLeaderOnlyViewsFullTimeTeacherInSameMajor() {
        when(organization.rolesForUser(10L)).thenReturn(List.of(role("MAJOR_LEADER", 7L)));
        when(organization.activeMajorForTeacher(2L)).thenReturn(7L);
        when(organization.isMajorActive(7L)).thenReturn(true);
        assertThat(service.require(teacher(10L, 1L), ResourceType.PAPER, 10L, AccessPermission.VIEW))
                .isNotNull();

        when(organization.activeMajorForTeacher(2L)).thenReturn(8L);
        assertDenied(service, teacher(10L, 1L), AccessPermission.VIEW);
    }

    @Test
    void inactiveMajorRemovesMajorLeaderScope() {
        when(organization.rolesForUser(10L)).thenReturn(List.of(role("MAJOR_LEADER", 7L)));
        when(organization.activeMajorForTeacher(2L)).thenReturn(null);
        when(organization.isMajorActive(7L)).thenReturn(false);

        assertDenied(service, teacher(10L, 1L), AccessPermission.VIEW);
    }

    @Test
    void downloadAndAiUseRequireAnActiveGrant() {
        when(organization.rolesForUser(10L)).thenReturn(List.of(role("DEAN", null)));
        when(grants.selectOne(any(Wrapper.class))).thenReturn(null);
        assertDenied(service, teacher(10L, 1L), AccessPermission.DOWNLOAD);

        ResourceAccessGrant grant = new ResourceAccessGrant();
        grant.setId(99L);
        grant.setEffectiveAt(LocalDateTime.now().minusDays(1));
        grant.setExpiresAt(LocalDateTime.now().plusDays(1));
        when(grants.selectOne(any(Wrapper.class))).thenReturn(grant);
        assertThat(service.require(teacher(10L, 1L), ResourceType.PAPER, 10L, AccessPermission.AI_USE)
                .grant().getId()).isEqualTo(99L);
    }

    @Test
    void attachmentRequiresItsOwnGrantAndDoesNotReuseParentGrant() {
        when(catalog.owner(ResourceType.FILE_ATTACHMENT, 101L)).thenReturn(2L);
        ResourceAccessPolicy attachmentPolicy = new ResourceAccessPolicy();
        attachmentPolicy.setOwnerTeacherId(2L);
        attachmentPolicy.setViewAllowed(true);
        attachmentPolicy.setDownloadAllowed(true);
        attachmentPolicy.setAiUseAllowed(true);
        when(policies.selectOne(any(Wrapper.class))).thenReturn(attachmentPolicy);

        ResourceAccessGrant parentGrant = new ResourceAccessGrant();
        parentGrant.setId(88L);
        parentGrant.setResourceType(ResourceType.PAPER.name());
        parentGrant.setResourceId(10L);
        when(grants.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> service.require(
                teacher(10L, 1L), ResourceType.FILE_ATTACHMENT, 101L, AccessPermission.DOWNLOAD))
                .isInstanceOf(BusinessException.class);
    }

    private CurrentUser teacher(Long userId, Long teacherId) {
        return new CurrentUser(userId, teacherId, "teacher", "t@example.com", "TEACHER", 0L);
    }

    private AcademicRoleAssignment role(String code, Long majorId) {
        AcademicRoleAssignment role = new AcademicRoleAssignment();
        role.setRoleCode(code);
        role.setMajorId(majorId);
        return role;
    }

    private void assertDenied(
            ResourceAuthorizationService target, CurrentUser actor, AccessPermission permission) {
        assertThatThrownBy(() -> target.require(actor, ResourceType.PAPER, 10L, permission))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
