package com.example.academicprofile.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.academicprofile.common.exception.BusinessException;
import com.example.academicprofile.teacher.TeacherProfile;
import com.example.academicprofile.teacher.TeacherProfileService;
import com.example.academicprofile.user.UserAccount;
import com.example.academicprofile.user.UserAccountService;
import com.example.academicprofile.user.UserRole;
import com.example.academicprofile.user.UserStatus;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserAccountService userAccountService;
    private final TeacherProfileService teacherProfileService;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserAccountService userAccountService,
            TeacherProfileService teacherProfileService) {
        this.jwtService = jwtService;
        this.userAccountService = userAccountService;
        this.teacherProfileService = teacherProfileService;
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            try {
                CurrentUser tokenUser = jwtService.parseToken(header.substring(7));
                CurrentUser currentUser = loadCurrentUser(tokenUser);
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        currentUser,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + currentUser.role())));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (BusinessException ex) {
                SecurityContextHolder.clearContext();
                writeUnauthorized(response);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private CurrentUser loadCurrentUser(CurrentUser tokenUser) {
        UserAccount user = userAccountService.findById(tokenUser.userId())
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid or expired token"));
        UserRole role;
        UserStatus status;
        try {
            role = UserRole.require(user.getRole());
            status = UserStatus.require(user.getStatus());
        } catch (BusinessException ex) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid or expired token");
        }
        if (status != UserStatus.ACTIVE
                || user.getAuthVersion() == null
                || !user.getAuthVersion().equals(tokenUser.authVersion())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid or expired token");
        }
        TeacherProfile profile = teacherProfileService.findByUserId(user.getId())
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid or expired token"));
        return new CurrentUser(
                user.getId(),
                profile.getId(),
                user.getUsername(),
                user.getEmail(),
                role.name(),
                user.getAuthVersion());
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"success\":false,\"message\":\"Authentication required\",\"data\":null}");
    }
}
