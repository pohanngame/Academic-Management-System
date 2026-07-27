package com.example.academicprofile.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.academicprofile.common.exception.BusinessException;
import com.example.academicprofile.security.CurrentUser;
import com.example.academicprofile.teacher.TeacherProfileService;
import com.example.academicprofile.user.UserAccount;
import com.example.academicprofile.user.UserAccountService;

class AdminUserServiceTest {

    private UserAccountService users;
    private AdminAuditService audit;
    private AdminUserService service;
    private CurrentUser operator;

    @BeforeEach
    void setUp() {
        users = org.mockito.Mockito.mock(UserAccountService.class);
        audit = org.mockito.Mockito.mock(AdminAuditService.class);
        TransactionTemplate transactions = org.mockito.Mockito.mock(TransactionTemplate.class);
        when(transactions.execute(org.mockito.ArgumentMatchers.<TransactionCallback<Object>>any()))
                .thenAnswer(invocation -> ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null));
        service = new AdminUserService(
                users, org.mockito.Mockito.mock(TeacherProfileService.class), audit, transactions);
        operator = new CurrentUser(1L, 10L, "admin", "admin@example.com", "SUPER_ADMIN", 2L);
    }

    @Test
    void cannotDisableSelf() {
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(admin(1L)));

        assertThatThrownBy(() -> service.changeStatus(operator, 1L, new AdminStatusRequest("DISABLED")))
                .isInstanceOf(BusinessException.class);
        verify(audit).record(1L, "USER_STATUS_CHANGE", 1L, false, "Administrative change rejected");
    }

    @Test
    void cannotDowngradeLastActiveAdmin() {
        when(users.findByIdForUpdate(2L)).thenReturn(Optional.of(admin(2L)));
        when(users.lockActiveSuperAdmins()).thenReturn(List.of(admin(2L)));

        assertThatThrownBy(() -> service.changeRole(operator, 2L, new AdminRoleRequest("TEACHER")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getMessage()).contains("last active"));
    }

    @Test
    void canDowngradeOneOfTwoAdminsAndInvalidatesTokens() {
        UserAccount target = admin(2L);
        when(users.findByIdForUpdate(2L)).thenReturn(Optional.of(target));
        when(users.lockActiveSuperAdmins()).thenReturn(List.of(admin(1L), target));

        AdminUserResponse response = service.changeRole(operator, 2L, new AdminRoleRequest("TEACHER"));

        assertThat(response.role()).isEqualTo("TEACHER");
        assertThat(target.getAuthVersion()).isEqualTo(3L);
        verify(users).update(target);
        verify(audit).record(1L, "USER_ROLE_CHANGE", 2L, true, "Role changed to TEACHER");
    }

    @Test
    void successAuditMustRunInsideBusinessTransaction() {
        AtomicBoolean insideTransaction = new AtomicBoolean();
        TransactionTemplate transactions = org.mockito.Mockito.mock(TransactionTemplate.class);
        when(transactions.execute(org.mockito.ArgumentMatchers.<TransactionCallback<Object>>any()))
                .thenAnswer(invocation -> {
                    insideTransaction.set(true);
                    try {
                        return ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null);
                    } finally {
                        insideTransaction.set(false);
                    }
                });
        AdminAuditService transactionalAudit = org.mockito.Mockito.mock(AdminAuditService.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            if (invocation.getArgument(3, Boolean.class) && !insideTransaction.get()) {
                throw new AssertionError("success audit executed after the business transaction");
            }
            return null;
        }).when(transactionalAudit).record(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyString());
        AdminUserService transactionalService = new AdminUserService(
                users, org.mockito.Mockito.mock(TeacherProfileService.class),
                transactionalAudit, transactions);
        UserAccount target = admin(2L);
        when(users.findByIdForUpdate(2L)).thenReturn(Optional.of(target));
        when(users.lockActiveSuperAdmins()).thenReturn(List.of(admin(1L), target));

        transactionalService.changeRole(operator, 2L, new AdminRoleRequest("TEACHER"));
    }

    private UserAccount admin(Long id) {
        UserAccount account = new UserAccount();
        account.setId(id);
        account.setUsername("admin-" + id);
        account.setEmail("admin-" + id + "@example.com");
        account.setRole("SUPER_ADMIN");
        account.setStatus("ACTIVE");
        account.setAuthVersion(2L);
        return account;
    }
}
