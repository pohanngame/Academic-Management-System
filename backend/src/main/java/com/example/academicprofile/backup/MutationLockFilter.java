package com.example.academicprofile.backup;

import java.io.IOException;
import java.util.Set;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class MutationLockFilter extends OncePerRequestFilter {

    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private final SystemMaintenanceLock maintenanceLock;

    public MutationLockFilter(SystemMaintenanceLock maintenanceLock) {
        this.maintenanceLock = maintenanceLock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (READ_METHODS.contains(request.getMethod())
                || request.getRequestURI().equals("/api/admin/backups/export")) {
            chain.doFilter(request, response);
            return;
        }
        maintenanceLock.lockMutation();
        try {
            chain.doFilter(request, response);
        } finally {
            maintenanceLock.unlockMutation();
        }
    }
}
