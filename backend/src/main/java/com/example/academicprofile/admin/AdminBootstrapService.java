package com.example.academicprofile.admin;

import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.academicprofile.common.exception.BusinessException;
import com.example.academicprofile.teacher.TeacherProfile;
import com.example.academicprofile.teacher.TeacherProfileService;
import com.example.academicprofile.user.UserAccount;
import com.example.academicprofile.user.UserAccountService;
import com.example.academicprofile.user.UserRole;
import com.example.academicprofile.user.UserStatus;

@Service
public class AdminBootstrapService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UserAccountService userAccountService;
    private final TeacherProfileService teacherProfileService;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrapService(
            UserAccountService userAccountService,
            TeacherProfileService teacherProfileService,
            PasswordEncoder passwordEncoder) {
        this.userAccountService = userAccountService;
        this.teacherProfileService = teacherProfileService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void execute(AdminBootstrapProperties properties) {
        validateCommon(properties);
        String action = properties.getAction().trim().toLowerCase(Locale.ROOT);
        switch (action) {
            case "create" -> createAdmin(properties);
            case "promote" -> promoteAdmin(properties);
            default -> throw new BusinessException("Bootstrap action must be create or promote");
        }
    }

    private void createAdmin(AdminBootstrapProperties properties) {
        requireText(properties.getDisplayName(), "Display name is required for create");
        if (userAccountService.existsByUsername(properties.getUsername().trim())) {
            throw new BusinessException("Bootstrap username already exists");
        }
        if (userAccountService.existsByEmail(properties.getEmail().trim())) {
            throw new BusinessException("Bootstrap email already exists");
        }

        UserAccount user = new UserAccount();
        user.setUsername(properties.getUsername().trim());
        user.setEmail(properties.getEmail().trim());
        user.setPasswordHash(passwordEncoder.encode(properties.getPassword()));
        user.setRole(UserRole.SUPER_ADMIN.name());
        user.setStatus(UserStatus.ACTIVE.name());
        user.setAuthVersion(0L);
        userAccountService.create(user);

        TeacherProfile profile = new TeacherProfile();
        profile.setUserId(user.getId());
        profile.setDisplayName(properties.getDisplayName().trim());
        profile.setPublicEnabled(Boolean.FALSE);
        teacherProfileService.create(profile);
    }

    private void promoteAdmin(AdminBootstrapProperties properties) {
        UserAccount user = userAccountService.findByUsernameOrEmail(properties.getUsername().trim())
                .orElseThrow(() -> new BusinessException("Bootstrap account not found"));
        if (!user.getUsername().equals(properties.getUsername().trim())
                || !user.getEmail().equals(properties.getEmail().trim())) {
            throw new BusinessException("Bootstrap username and email do not identify the same account");
        }
        UserRole currentRole = UserRole.require(user.getRole());
        if (currentRole == UserRole.SUPER_ADMIN) {
            throw new BusinessException("Bootstrap account is already SUPER_ADMIN");
        }
        teacherProfileService.findByUserId(user.getId())
                .orElseThrow(() -> new BusinessException("Bootstrap teacher profile not found"));

        user.setPasswordHash(passwordEncoder.encode(properties.getPassword()));
        user.setRole(UserRole.SUPER_ADMIN.name());
        user.setStatus(UserStatus.ACTIVE.name());
        user.setAuthVersion(nextVersion(user.getAuthVersion()));
        userAccountService.update(user);
    }

    private void validateCommon(AdminBootstrapProperties properties) {
        requireText(properties.getAction(), "Bootstrap action is required");
        requireText(properties.getUsername(), "Bootstrap username is required");
        requireText(properties.getEmail(), "Bootstrap email is required");
        requireText(properties.getPassword(), "Bootstrap password is required");
        String username = properties.getUsername().trim();
        String email = properties.getEmail().trim();
        if (username.length() < 3 || username.length() > 64) {
            throw new BusinessException("Bootstrap username length is invalid");
        }
        if (email.length() > 128 || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new BusinessException("Bootstrap email format is invalid");
        }
        if (properties.getPassword().length() < 8 || properties.getPassword().length() > 64) {
            throw new BusinessException("Bootstrap password length is invalid");
        }
        if (StringUtils.hasText(properties.getDisplayName())
                && properties.getDisplayName().trim().length() > 128) {
            throw new BusinessException("Bootstrap display name length is invalid");
        }
    }

    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(message);
        }
    }

    private long nextVersion(Long currentVersion) {
        return currentVersion == null ? 1L : currentVersion + 1L;
    }
}
