package com.example.academicprofile.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.academicprofile.common.exception.BusinessException;
import com.example.academicprofile.teacher.TeacherProfile;
import com.example.academicprofile.teacher.TeacherProfileService;
import com.example.academicprofile.user.UserAccount;
import com.example.academicprofile.user.UserAccountService;

class AdminBootstrapServiceTest {

    private UserAccountService users;
    private TeacherProfileService teachers;
    private PasswordEncoder passwords;
    private AdminBootstrapService service;

    @BeforeEach
    void setUp() {
        users = org.mockito.Mockito.mock(UserAccountService.class);
        teachers = org.mockito.Mockito.mock(TeacherProfileService.class);
        passwords = org.mockito.Mockito.mock(PasswordEncoder.class);
        service = new AdminBootstrapService(users, teachers, passwords);
        when(passwords.encode("temporary-password")).thenReturn("bcrypt");
    }

    @Test
    void createsAdminAndTeacherProfileWithoutExposingPassword() {
        AdminBootstrapProperties properties = properties("create");
        AtomicReference<UserAccount> account = new AtomicReference<>();
        org.mockito.Mockito.doAnswer(invocation -> {
            UserAccount user = invocation.getArgument(0);
            user.setId(9L);
            account.set(user);
            return null;
        }).when(users).create(any());

        service.execute(properties);

        assertThat(account.get().getRole()).isEqualTo("SUPER_ADMIN");
        assertThat(account.get().getStatus()).isEqualTo("ACTIVE");
        assertThat(account.get().getPasswordHash()).isEqualTo("bcrypt");
        verify(teachers).create(any(TeacherProfile.class));
    }

    @Test
    void promotesExistingTeacherAndInvalidatesOldTokens() {
        AdminBootstrapProperties properties = properties("promote");
        UserAccount user = teacher();
        when(users.findByUsernameOrEmail("admin")).thenReturn(Optional.of(user));
        when(teachers.findByUserId(3L)).thenReturn(Optional.of(new TeacherProfile()));

        service.execute(properties);

        assertThat(user.getRole()).isEqualTo("SUPER_ADMIN");
        assertThat(user.getStatus()).isEqualTo("ACTIVE");
        assertThat(user.getAuthVersion()).isEqualTo(5L);
        verify(users).update(user);
    }

    @Test
    void incompleteBootstrapFailsBeforeWriting() {
        AdminBootstrapProperties properties = properties("create");
        properties.setPassword("");

        assertThatThrownBy(() -> service.execute(properties))
                .isInstanceOf(BusinessException.class);
        verify(users, never()).create(any());
    }

    private AdminBootstrapProperties properties(String action) {
        AdminBootstrapProperties properties = new AdminBootstrapProperties();
        properties.setAction(action);
        properties.setUsername("admin");
        properties.setEmail("admin@example.com");
        properties.setPassword("temporary-password");
        properties.setDisplayName("System Administrator");
        return properties;
    }

    private UserAccount teacher() {
        UserAccount user = new UserAccount();
        user.setId(3L);
        user.setUsername("admin");
        user.setEmail("admin@example.com");
        user.setRole("TEACHER");
        user.setStatus("ACTIVE");
        user.setAuthVersion(4L);
        return user;
    }
}
