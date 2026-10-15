package com.example.academicprofile.timetable;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.example.academicprofile.SecurityConfig;
import com.example.academicprofile.backup.SystemMaintenanceLock;
import com.example.academicprofile.organization.OrganizationService;
import com.example.academicprofile.security.JwtAuthenticationFilter;
import com.example.academicprofile.timetable.TimetableDtos.CommonFreeResponse;

/**
 * 公共空闲要求登录；教师与超级管理员都可查询（契约：全部接口要求登录本系统）。
 */
@WebMvcTest(
        controllers = TimetableCommonFreeController.class,
        excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import(SecurityConfig.class)
class TimetableCommonFreeAuthorizationTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private JwtAuthenticationFilter jwtFilter;
    @MockBean
    private SystemMaintenanceLock maintenanceLock;
    @MockBean
    private TimetableCommonFreeService service;
    @MockBean
    private TimetableCommonFreeExcelService excelService;
    @MockBean
    private OrganizationService organizationService;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            jakarta.servlet.FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtFilter).doFilter(any(), any(), any());
        when(service.query(any(), any(Integer.class), any(), any()))
                .thenReturn(new CommonFreeResponse("2026-2027-1", 5, false, java.util.List.of(),
                        java.util.List.of()));
    }

    @Test
    void unauthenticatedQueryIsRejected() throws Exception {
        mvc.perform(get("/api/timetable/common-free")
                        .param("semesterCode", "2026-2027-1")
                        .param("week", "5")
                        .param("teacherIds", "7"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void teacherCanQuery() throws Exception {
        mvc.perform(get("/api/timetable/common-free")
                        .param("semesterCode", "2026-2027-1")
                        .param("week", "5")
                        .param("teacherIds", "7")
                        .with(user("teacher").roles("TEACHER")))
                .andExpect(status().isOk());
    }

    @Test
    void superAdminCanQuery() throws Exception {
        mvc.perform(get("/api/timetable/common-free")
                        .param("semesterCode", "2026-2027-1")
                        .param("week", "5")
                        .param("majorId", "3")
                        .with(user("admin").roles("SUPER_ADMIN")))
                .andExpect(status().isOk());
    }
}
