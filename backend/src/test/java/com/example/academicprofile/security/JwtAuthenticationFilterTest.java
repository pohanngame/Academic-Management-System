package com.example.academicprofile.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import com.example.academicprofile.teacher.TeacherProfile;
import com.example.academicprofile.teacher.TeacherProfileService;
import com.example.academicprofile.user.UserAccount;
import com.example.academicprofile.user.UserAccountService;

class JwtAuthenticationFilterTest {

    private JwtService jwt;
    private UserAccountService users;
    private TeacherProfileService teachers;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        jwt = org.mockito.Mockito.mock(JwtService.class);
        users = org.mockito.Mockito.mock(UserAccountService.class);
        teachers = org.mockito.Mockito.mock(TeacherProfileService.class);
        filter = new JwtAuthenticationFilter(jwt, users, teachers);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void databaseRoleOverridesForgedTokenRole() throws Exception {
        CurrentUser token = new CurrentUser(1L, 999L, "old", "old@example.com", "SUPER_ADMIN", 7L);
        when(jwt.parseToken("signed-token")).thenReturn(token);
        when(users.findById(1L)).thenReturn(Optional.of(user("TEACHER", "ACTIVE", 7L)));
        when(teachers.findByUserId(1L)).thenReturn(Optional.of(profile(12L)));
        MockHttpServletResponse response = runFilter();

        assertThat(response.getStatus()).isEqualTo(200);
        CurrentUser authenticated = (CurrentUser) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        assertThat(authenticated.role()).isEqualTo("TEACHER");
        assertThat(authenticated.teacherId()).isEqualTo(12L);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_TEACHER");
    }

    @Test
    void disabledAccountInvalidatesOldTokenImmediately() throws Exception {
        when(jwt.parseToken("signed-token"))
                .thenReturn(new CurrentUser(1L, 12L, "user", "user@example.com", "TEACHER", 7L));
        when(users.findById(1L)).thenReturn(Optional.of(user("TEACHER", "DISABLED", 7L)));

        MockHttpServletResponse response = runFilter();

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).doesNotContain("signed-token");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void authVersionChangeInvalidatesOldTokenImmediately() throws Exception {
        when(jwt.parseToken("signed-token"))
                .thenReturn(new CurrentUser(1L, 12L, "user", "user@example.com", "SUPER_ADMIN", 6L));
        when(users.findById(1L)).thenReturn(Optional.of(user("TEACHER", "ACTIVE", 7L)));

        MockHttpServletResponse response = runFilter();

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void unknownStoredRoleFailsClosed() throws Exception {
        when(jwt.parseToken("signed-token"))
                .thenReturn(new CurrentUser(1L, 12L, "user", "user@example.com", "TEACHER", 7L));
        when(users.findById(1L)).thenReturn(Optional.of(user("ROOT", "ACTIVE", 7L)));

        assertThat(runFilter().getStatus()).isEqualTo(401);
    }

    private MockHttpServletResponse runFilter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/overview");
        request.addHeader("Authorization", "Bearer signed-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
        });
        return response;
    }

    private UserAccount user(String role, String status, long authVersion) {
        UserAccount user = new UserAccount();
        user.setId(1L);
        user.setUsername("current");
        user.setEmail("current@example.com");
        user.setRole(role);
        user.setStatus(status);
        user.setAuthVersion(authVersion);
        return user;
    }

    private TeacherProfile profile(long id) {
        TeacherProfile profile = new TeacherProfile();
        profile.setId(id);
        profile.setUserId(1L);
        return profile;
    }
}
