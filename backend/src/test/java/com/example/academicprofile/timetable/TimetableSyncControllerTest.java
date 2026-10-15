package com.example.academicprofile.timetable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.example.academicprofile.common.ApiResponse;
import com.example.academicprofile.security.CurrentUser;
import com.example.academicprofile.timetable.TimetableDtos.CaptchaRequest;
import com.example.academicprofile.timetable.TimetableDtos.CaptchaResponse;
import com.example.academicprofile.timetable.TimetableDtos.SyncRequest;
import com.example.academicprofile.timetable.TimetableDtos.SyncResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

/** 直接调用控制器：断言响应结构里不含任何教务凭据字段。 */
class TimetableSyncControllerTest {

    private TimetableSyncService service;
    private TimetableSyncController controller;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = Mockito.mock(TimetableSyncService.class);
        controller = new TimetableSyncController(service);
        CurrentUser principal = new CurrentUser(1L, 1L, "teacher", "t@example.com", "TEACHER", 0L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_TEACHER"))));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void captchaResponseShapeMatchesContract() {
        when(service.startAttempt(any(), any()))
                .thenReturn(new CaptchaResponse(
                        "attempt-1", true, null, true, "YmctaW1hZ2U=", "cGllY2UtaW1hZ2U="));

        ApiResponse<CaptchaResponse> response =
                controller.captcha(new CaptchaRequest("jg-account"));

        assertThat(response.success()).isTrue();
        assertThat(response.data().attemptId()).isEqualTo("attempt-1");
        assertThat(response.data().captchaRequired()).isTrue();
        assertThat(response.data().sliderRequired()).isTrue();
        assertThat(response.data().sliderBackgroundBase64()).isEqualTo("YmctaW1hZ2U=");
        assertThat(response.data().sliderPieceBase64()).isEqualTo("cGllY2UtaW1hZ2U=");
    }

    @Test
    void syncResponseCarriesNoCredentials() throws Exception {
        when(service.sync(any(), any()))
                .thenReturn(new SyncResponse(12L, "2026-2027-1", 3, 9, java.util.List.of("李四")));

        ApiResponse<SyncResponse> response = controller.sync(new SyncRequest(
                "attempt-1", "2026-2027-1", "super-secret-password", "1234",
                null, null, null));

        assertThat(response.data().batchId()).isEqualTo(12L);
        assertThat(response.data().teacherCount()).isEqualTo(3);
        assertThat(response.data().cellCount()).isEqualTo(9);
        assertThat(response.data().unmappedNames()).containsExactly("李四");

        // 序列化后的响应体不得出现密码，也不得有 Cookie/Session/票据字段。
        String json = objectMapper.writeValueAsString(response);
        assertThat(json).doesNotContain("super-secret-password");
        assertThat(json.toLowerCase())
                .doesNotContain("cookie")
                .doesNotContain("session")
                .doesNotContain("ticket")
                .doesNotContain("password");
    }

    @Test
    void currentUserIsForwardedToService() {
        when(service.sync(any(), any()))
                .thenReturn(new SyncResponse(1L, "2026-2027-1", 0, 0, java.util.List.of()));

        controller.sync(new SyncRequest("a", "2026-2027-1", "p", null, null, null, null));

        org.mockito.ArgumentCaptor<Long> actor = org.mockito.ArgumentCaptor.forClass(Long.class);
        org.mockito.Mockito.verify(service).sync(actor.capture(), any());
        assertThat(actor.getValue()).isEqualTo(1L);
    }
}
