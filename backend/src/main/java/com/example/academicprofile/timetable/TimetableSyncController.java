package com.example.academicprofile.timetable;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.academicprofile.common.ApiResponse;
import com.example.academicprofile.security.SecurityUtils;
import com.example.academicprofile.timetable.TimetableDtos.CaptchaRequest;
import com.example.academicprofile.timetable.TimetableDtos.CaptchaResponse;
import com.example.academicprofile.timetable.TimetableDtos.SyncRequest;
import com.example.academicprofile.timetable.TimetableDtos.SyncResponse;

import jakarta.validation.Valid;

/**
 * 登录本系统的用户均可调用（由 SecurityConfig 的 anyRequest().authenticated() 保证）。
 * 响应只回批次统计，绝不回显教务凭据、Cookie、Session 或票据。
 */
@RestController
@RequestMapping("/api/timetable/sync")
public class TimetableSyncController {
    private final TimetableSyncService service;

    public TimetableSyncController(TimetableSyncService service) {
        this.service = service;
    }

    @PostMapping("/captcha")
    public ApiResponse<CaptchaResponse> captcha(@Valid @RequestBody CaptchaRequest request) {
        return ApiResponse.ok(service.startAttempt(SecurityUtils.currentUser().userId(), request));
    }

    @PostMapping
    public ApiResponse<SyncResponse> sync(@Valid @RequestBody SyncRequest request) {
        return ApiResponse.ok(service.sync(SecurityUtils.currentUser().userId(), request));
    }
}
