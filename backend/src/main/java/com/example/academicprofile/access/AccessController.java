package com.example.academicprofile.access;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.academicprofile.access.AccessDtos.AccessRequestResponse;
import com.example.academicprofile.access.AccessDtos.AccessAuditResponse;
import com.example.academicprofile.access.AccessDtos.CreateRequest;
import com.example.academicprofile.access.AccessDtos.DecisionRequest;
import com.example.academicprofile.access.AccessDtos.GrantResponse;
import com.example.academicprofile.access.AccessDtos.PolicyBatchRequest;
import com.example.academicprofile.access.AccessDtos.AccessCapabilitiesResponse;
import com.example.academicprofile.access.AccessDtos.ApprovalCandidateResponse;
import com.example.academicprofile.common.ApiResponse;
import com.example.academicprofile.organization.OrganizationService;
import com.example.academicprofile.security.SecurityUtils;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/access")
public class AccessController {
    private final AccessRequestService requests;
    private final ResourcePolicyService policies;
    private final ResourceAccessAuditService audits;
    private final SharedResourceService shared;
    private final AiAttachmentOptionService aiAttachments;
    private final OrganizationService organization;

    public AccessController(
            AccessRequestService requests,
            ResourcePolicyService policies,
            ResourceAccessAuditService audits,
            SharedResourceService shared,
            AiAttachmentOptionService aiAttachments,
            OrganizationService organization) {
        this.requests = requests;
        this.policies = policies;
        this.audits = audits;
        this.shared = shared;
        this.aiAttachments = aiAttachments;
        this.organization = organization;
    }

    @PostMapping("/requests")
    public ApiResponse<List<AccessRequestResponse>> create(@Valid @RequestBody CreateRequest request) {
        var actor = SecurityUtils.currentUser();
        try {
            return ApiResponse.ok(requests.create(actor, request));
        } catch (RuntimeException ex) {
            request.ownerTeacherIds().forEach(owner -> audits.tryRecordFailure(
                    actor, owner, "REQUEST_CREATE", null, null, null, null));
            throw ex;
        }
    }

    @GetMapping("/requests/mine")
    public ApiResponse<Page<AccessRequestResponse>> mine(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(requests.myRequests(SecurityUtils.currentUser().teacherId(), page, size));
    }

    @GetMapping("/requests/pending")
    public ApiResponse<Page<AccessRequestResponse>> pending(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(requests.pendingForOwner(SecurityUtils.currentUser().teacherId(), page, size));
    }

    @GetMapping("/requests/{id}/candidates")
    public ApiResponse<List<ApprovalCandidateResponse>> approvalCandidates(@PathVariable Long id) {
        return ApiResponse.ok(requests.approvalCandidates(SecurityUtils.currentUser(), id));
    }

    @PatchMapping("/requests/{id}/decision")
    public ApiResponse<AccessRequestResponse> decide(
            @PathVariable Long id, @Valid @RequestBody DecisionRequest request) {
        var actor = SecurityUtils.currentUser();
        try {
            return ApiResponse.ok(requests.decide(actor, id, request));
        } catch (RuntimeException ex) {
            audits.tryRecordFailure(actor, actor.teacherId(),
                    request.approve() ? "REQUEST_APPROVE" : "REQUEST_REJECT",
                    null, null, id, null);
            throw ex;
        }
    }

    @DeleteMapping("/requests/{id}")
    public ApiResponse<Void> cancel(@PathVariable Long id) {
        var actor = SecurityUtils.currentUser();
        try {
            requests.cancel(actor, id);
        } catch (RuntimeException ex) {
            audits.tryRecordFailure(
                    actor, actor.teacherId(), "REQUEST_CANCEL", null, null, id, null);
            throw ex;
        }
        return ApiResponse.ok(null);
    }

    @GetMapping("/grants")
    public ApiResponse<Page<GrantResponse>> grants(
            @RequestParam(defaultValue = "ACTIVE") String state,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(requests.grants(SecurityUtils.currentUser().teacherId(), state, page, size));
    }

    @DeleteMapping("/grants/{id}")
    public ApiResponse<Void> revoke(@PathVariable Long id) {
        var actor = SecurityUtils.currentUser();
        try {
            requests.revoke(actor, id);
        } catch (RuntimeException ex) {
            audits.tryRecordFailure(
                    actor, actor.teacherId(), "GRANT_REVOKE", null, null, null, id);
            throw ex;
        }
        return ApiResponse.ok(null);
    }

    @PutMapping("/policies")
    public ApiResponse<List<ResourceAccessPolicy>> policies(@Valid @RequestBody PolicyBatchRequest request) {
        return ApiResponse.ok(policies.updateOwned(SecurityUtils.currentUser(), request.items()));
    }

    @GetMapping("/policies")
    public ApiResponse<List<ResourceAccessPolicy>> policies(@RequestParam ResourceType resourceType) {
        return ApiResponse.ok(policies.listOwned(
                SecurityUtils.currentUser().teacherId(), resourceType));
    }

    @GetMapping("/audits")
    public ApiResponse<Page<AccessAuditResponse>> audits(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(audits.pageForOwner(SecurityUtils.currentUser().teacherId(), page, size));
    }

    @GetMapping("/directory")
    public ApiResponse<SharedResourceService.PageResult<TeacherDirectoryMapper.TeacherDirectoryItem>> directory(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "ALL") String scope,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(shared.directory(SecurityUtils.currentUser(), search, scope, page, size));
    }

    @GetMapping("/capabilities")
    public ApiResponse<AccessCapabilitiesResponse> capabilities() {
        return ApiResponse.ok(organization.accessCapabilities(SecurityUtils.currentUser()));
    }

    @GetMapping("/attachments/ai-eligible")
    public ApiResponse<List<AiAttachmentOptionService.AiAttachmentOption>> aiEligibleAttachments() {
        return ApiResponse.ok(aiAttachments.list(SecurityUtils.currentUser()));
    }

    @GetMapping("/resources")
    public ApiResponse<SharedResourceService.PageResult<java.util.Map<String, Object>>> resources(
            @RequestParam Long ownerTeacherId,
            @RequestParam ResourceType resourceType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(shared.list(
                SecurityUtils.currentUser(), ownerTeacherId, resourceType, page, size));
    }

    @GetMapping("/resources/{resourceType}/{id}")
    public ApiResponse<java.util.Map<String, Object>> resource(
            @PathVariable ResourceType resourceType, @PathVariable Long id) {
        return ApiResponse.ok(shared.detail(SecurityUtils.currentUser(), resourceType, id));
    }
}
