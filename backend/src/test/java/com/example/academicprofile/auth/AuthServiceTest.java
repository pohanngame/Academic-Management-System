package com.example.academicprofile.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.academicprofile.common.exception.BusinessException;
import com.example.academicprofile.organization.OrganizationService;
import com.example.academicprofile.security.CurrentUser;
import com.example.academicprofile.security.JwtService;
import com.example.academicprofile.teacher.TeacherProfile;
import com.example.academicprofile.teacher.TeacherProfileService;
import com.example.academicprofile.user.UserAccount;
import com.example.academicprofile.user.UserAccountService;

class AuthServiceTest {

    private UserAccountService users;
    private TeacherProfileService teachers;
    private PasswordEncoder passwords;
    private JwtService jwt;
    private OrganizationService organization;
    private AuthService service;

    @BeforeEach
    void setUp() {
        users = org.mockito.Mockito.mock(UserAccountService.class);
        teachers = org.mockito.Mockito.mock(TeacherProfileService.class);
        passwords = org.mockito.Mockito.mock(PasswordEncoder.class);
        jwt = org.mockito.Mockito.mock(JwtService.class);
        organization = org.mockito.Mockito.mock(OrganizationService.class);
        service = new AuthService(users, teachers, passwords, jwt, organization);
    }

    @Test
    void registrationAlwaysCreatesTeacher() {
        when(passwords.encode("password-123")).thenReturn("bcrypt");
        AtomicReference<UserAccount> captured = new AtomicReference<>();
        org.mockito.Mockito.doAnswer(invocation -> {
            UserAccount user = invocation.getArgument(0);
            user.setId(11L);
            captured.set(user);
            return null;
        }).when(users).create(any());
        org.mockito.Mockito.doAnswer(invocation -> {
            TeacherProfile profile = invocation.getArgument(0);
            profile.setId(22L);
            return null;
        }).when(teachers).create(any());
        when(jwt.generateToken(any(CurrentUser.class))).thenReturn("token");

        AuthResponse response = service.register(new RegisterRequest(
                "teacher", "teacher@example.com", "password-123", "password-123",
                "Teacher", null, null));

        assertThat(captured.get().getRole()).isEqualTo("TEACHER");
        assertThat(captured.get().getStatus()).isEqualTo("ACTIVE");
        assertThat(captured.get().getAuthVersion()).isZero();
        assertThat(response.user().role()).isEqualTo("TEACHER");
        verify(organization).assignDefaultRole(11L);
    }

    @Test
    void disabledUserCannotLogin() {
        UserAccount user = user("DISABLED", "TEACHER");
        when(users.findByUsernameOrEmail("teacher")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login(new LoginRequest("teacher", "password-123")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void unknownRoleFailsWithoutIssuingToken() {
        UserAccount user = user("ACTIVE", "ROOT");
        when(users.findByUsernameOrEmail("teacher")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login(new LoginRequest("teacher", "password-123")))
                .isInstanceOf(BusinessException.class);
        verify(users).findByUsernameOrEmail("teacher");
    }

    private UserAccount user(String status, String role) {
        UserAccount user = new UserAccount();
        user.setId(1L);
        user.setUsername("teacher");
        user.setEmail("teacher@example.com");
        user.setPasswordHash("bcrypt");
        user.setStatus(status);
        user.setRole(role);
        user.setAuthVersion(0L);
        return user;
    }
}
