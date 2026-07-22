package com.example.academicprofile.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.academicprofile.common.exception.BusinessException;
import com.example.academicprofile.security.CurrentUser;
import com.example.academicprofile.security.JwtService;
import com.example.academicprofile.teacher.TeacherProfile;
import com.example.academicprofile.teacher.TeacherProfileService;
import com.example.academicprofile.user.UserAccount;
import com.example.academicprofile.user.UserAccountService;

@Service
public class AuthService {

    private static final String DEFAULT_ROLE = "TEACHER";
    private static final String ACTIVE_STATUS = "ACTIVE";

    private final UserAccountService userAccountService;
    private final TeacherProfileService teacherProfileService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserAccountService userAccountService,
            TeacherProfileService teacherProfileService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.userAccountService = userAccountService;
        this.teacherProfileService = teacherProfileService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new BusinessException("Passwords do not match");
        }
        if (userAccountService.existsByUsername(request.username())) {
            throw new BusinessException("Username already exists");
        }
        if (userAccountService.existsByEmail(request.email())) {
            throw new BusinessException("Email already exists");
        }

        UserAccount user = new UserAccount();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(DEFAULT_ROLE);
        user.setStatus(ACTIVE_STATUS);
        userAccountService.create(user);

        TeacherProfile teacherProfile = new TeacherProfile();
        teacherProfile.setUserId(user.getId());
        teacherProfile.setDisplayName(request.displayName());
        teacherProfile.setDepartment(blankToNull(request.department()));
        teacherProfile.setTitle(blankToNull(request.title()));
        teacherProfile.setPublicEnabled(false);
        teacherProfileService.create(teacherProfile);

        AuthUserResponse authUser = toAuthUser(user, teacherProfile);
        String token = jwtService.generateToken(new CurrentUser(
                authUser.userId(),
                authUser.teacherId(),
                authUser.username(),
                authUser.email(),
                authUser.role()));
        return new AuthResponse(token, authUser);
    }

    public AuthResponse login(LoginRequest request) {
        UserAccount user = userAccountService.findByUsernameOrEmail(request.usernameOrEmail())
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid username/email or password"));
        if (!ACTIVE_STATUS.equals(user.getStatus())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Account is disabled");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid username/email or password");
        }
        TeacherProfile teacherProfile = teacherProfileService.findByUserId(user.getId())
                .orElseThrow(() -> new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "Teacher profile not found"));
        AuthUserResponse authUser = toAuthUser(user, teacherProfile);
        String token = jwtService.generateToken(new CurrentUser(
                authUser.userId(),
                authUser.teacherId(),
                authUser.username(),
                authUser.email(),
                authUser.role()));
        return new AuthResponse(token, authUser);
    }

    public AuthUserResponse me(CurrentUser currentUser) {
        UserAccount user = userAccountService.findById(currentUser.userId())
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Current user not found"));
        TeacherProfile teacherProfile = teacherProfileService.findByUserId(user.getId())
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Current teacher profile not found"));
        return toAuthUser(user, teacherProfile);
    }

    private AuthUserResponse toAuthUser(UserAccount user, TeacherProfile teacherProfile) {
        return new AuthUserResponse(
                user.getId(),
                teacherProfile.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                teacherProfile.getDisplayName());
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
