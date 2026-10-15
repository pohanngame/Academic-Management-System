package com.example.academicprofile.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.example.academicprofile.SecurityConfig;
import com.example.academicprofile.backup.BackupController;
import com.example.academicprofile.backup.BackupService;
import com.example.academicprofile.backup.RestoreStagingController;
import com.example.academicprofile.backup.RestoreStagingService;
import com.example.academicprofile.backup.SystemMaintenanceLock;
import com.example.academicprofile.security.JwtAuthenticationFilter;
import com.example.academicprofile.timetable.TimetableTeacherLinkController;
import com.example.academicprofile.timetable.TimetableTeacherLinkService;

@WebMvcTest(
        controllers = {AdminController.class, BackupController.class, RestoreStagingController.class,
                TimetableTeacherLinkController.class},
        excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import(SecurityConfig.class)
class AdminAuthorizationTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private JwtAuthenticationFilter jwtFilter;
    @MockBean
    private AdminOverviewService overview;
    @MockBean
    private AdminUserService users;
    @MockBean
    private AdminAuditService audit;
    @MockBean
    private BackupService backups;
    @MockBean
    private RestoreStagingService restoreStaging;
    @MockBean
    private SystemMaintenanceLock maintenanceLock;
    @MockBean
    private TimetableTeacherLinkService timetableTeacherLinks;

    @BeforeEach
    void passThroughJwtFilter() throws Exception {
        doAnswer(invocation -> {
            jakarta.servlet.FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtFilter).doFilter(any(), any(), any());
    }

    @Test
    void unauthenticatedAdminRequestReturns401() throws Exception {
        mvc.perform(get("/api/admin/overview")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/restores/staged")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/timetable/teacher-links")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @MethodSource("adminRequests")
    void teacherCannotAccessAnyAdminEndpoint(MockHttpServletRequestBuilder request) throws Exception {
        mvc.perform(request.with(user("teacher").roles("TEACHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void superAdminCanAccessOverview() throws Exception {
        when(overview.get()).thenReturn(new AdminOverviewResponse(
                0, 0, 0, 0, 0, 0, 0, null));
        mvc.perform(get("/api/admin/overview").with(user("admin").roles("SUPER_ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void superAdminCanListTimetableTeacherLinks() throws Exception {
        when(timetableTeacherLinks.list()).thenReturn(java.util.List.of());
        mvc.perform(get("/api/admin/timetable/teacher-links")
                        .with(user("admin").roles("SUPER_ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void superAdminCannotAccessTeacherPrivateApi() throws Exception {
        mvc.perform(get("/api/teacher/profile").with(user("admin").roles("SUPER_ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/files/1/preview").with(user("admin").roles("SUPER_ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/access/audits").with(user("admin").roles("SUPER_ADMIN")))
                .andExpect(status().isForbidden());
    }

    static Stream<Arguments> adminRequests() {
        return Stream.of(
                Arguments.of(get("/api/admin/overview")),
                Arguments.of(get("/api/admin/users")),
                Arguments.of(get("/api/admin/audit-logs")),
                Arguments.of(patch("/api/admin/users/2/role")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"SUPER_ADMIN\"}")),
                Arguments.of(patch("/api/admin/users/2/status")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISABLED\"}")),
                Arguments.of(post("/api/admin/backups/export")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"not-real\"}")),
                Arguments.of(get("/api/admin/restores/staged")),
                Arguments.of(get("/api/admin/timetable/teacher-links")),
                Arguments.of(post("/api/admin/timetable/teacher-links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceName\":\"张三\",\"teacherId\":1}")),
                Arguments.of(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/admin/timetable/teacher-links")
                        .param("sourceName", "张三")),
                Arguments.of(post("/api/admin/restores/staged")
                        .contentType(MediaType.MULTIPART_FORM_DATA)),
                Arguments.of(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/admin/restores/staged/11111111-1111-1111-1111-111111111111")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"not-real\"}")));
    }
}
