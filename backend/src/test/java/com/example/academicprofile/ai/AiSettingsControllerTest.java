package com.example.academicprofile.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.example.academicprofile.security.CurrentUser;

class AiSettingsControllerTest {

    private AiSettingsService service;
    private AiSettingsController controller;

    @BeforeEach
    void setUp() {
        service = mock(AiSettingsService.class);
        controller = new AiSettingsController(service);
        CurrentUser teacher = new CurrentUser(5L, 42L, "teacher-a", "teacher@example.test", "TEACHER");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(teacher, null));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void everySettingsOperationUsesAuthenticatedTeacherId() {
        AiSettingsRequest request = new AiSettingsRequest(
                "provider",
                "https://api.example.test/v1",
                "model",
                "request-only-key");
        when(service.getSettings(42L)).thenReturn(AiSettingsResponse.empty());
        when(service.saveSettings(42L, request)).thenReturn(AiSettingsResponse.empty());
        when(service.testConnection(42L)).thenReturn(new AiConnectionTestResult(true, "连接测试成功"));

        assertFalse(controller.getSettings().data().configured());
        controller.saveSettings(request);
        controller.testConnection();
        controller.deleteSettings();

        verify(service).getSettings(42L);
        verify(service).saveSettings(42L, request);
        verify(service).testConnection(42L);
        verify(service).deleteSettings(42L);
    }
}
