package com.example.academicprofile.access;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.academicprofile.common.exception.BusinessException;
import com.example.academicprofile.security.CurrentUser;

@Service
public class ResourcePolicyService {
    private static final int MAX_BATCH = 100;
    private final ResourceCatalog catalog;
    private final ResourceAccessPolicyMapper policies;
    private final ResourceAccessGrantMapper grants;
    private final ResourceAccessAuditService audits;
    private final TransactionTemplate transactions;

    public ResourcePolicyService(
            ResourceCatalog catalog,
            ResourceAccessPolicyMapper policies,
            ResourceAccessGrantMapper grants,
            ResourceAccessAuditService audits,
            TransactionTemplate transactions) {
        this.catalog = catalog;
        this.policies = policies;
        this.grants = grants;
        this.audits = audits;
        this.transactions = transactions;
    }

    @Transactional
    public void createDefault(Long ownerTeacherId, ResourceType type, Long resourceId) {
        ResourceAccessPolicy policy = new ResourceAccessPolicy();
        policy.setOwnerTeacherId(ownerTeacherId);
        policy.setResourceType(type.name());
        policy.setResourceId(resourceId);
        policy.setViewAllowed(Boolean.TRUE);
        policy.setDownloadAllowed(Boolean.FALSE);
        policy.setAiUseAllowed(Boolean.FALSE);
        policies.insert(policy);
    }

    public List<ResourceAccessPolicy> updateOwned(
            CurrentUser actor, List<PolicyUpdate> updates) {
        try {
            return transactions.execute(status -> updateInTransaction(actor, updates));
        } catch (RuntimeException ex) {
            audits.tryRecordFailure(
                    actor, actor.teacherId(), "POLICY_UPDATE", null, null, null, null);
            throw ex;
        }
    }

    private List<ResourceAccessPolicy> updateInTransaction(
            CurrentUser actor, List<PolicyUpdate> updates) {
        Long ownerTeacherId = actor.teacherId();
        if (updates == null || updates.isEmpty() || updates.size() > MAX_BATCH) {
            throw new BusinessException("Policy update batch must contain 1-100 resources");
        }
        return updates.stream().map(update -> {
            ResourceType type = ResourceType.require(update.resourceType());
            Long actualOwner = catalog.owner(type, update.resourceId());
            if (!ownerTeacherId.equals(actualOwner)) {
                throw new BusinessException("All resources must belong to the current teacher");
            }
            ResourceAccessPolicy policy = policies.selectOne(new LambdaQueryWrapper<ResourceAccessPolicy>()
                    .eq(ResourceAccessPolicy::getResourceType, type.name())
                    .eq(ResourceAccessPolicy::getResourceId, update.resourceId()));
            if (policy == null) {
                throw new BusinessException("Resource access policy is unavailable");
            }
            boolean oldView = Boolean.TRUE.equals(policy.getViewAllowed());
            boolean oldDownload = Boolean.TRUE.equals(policy.getDownloadAllowed());
            boolean oldAi = Boolean.TRUE.equals(policy.getAiUseAllowed());
            policy.setViewAllowed(update.viewAllowed());
            policy.setDownloadAllowed(update.downloadAllowed());
            policy.setAiUseAllowed(update.aiUseAllowed());
            policies.updateById(policy);
            if (oldView && !update.viewAllowed()) {
                revoke(type, update.resourceId(), null);
            } else {
                if (oldDownload && !update.downloadAllowed()) {
                    revoke(type, update.resourceId(), AccessPermission.DOWNLOAD);
                }
                if (oldAi && !update.aiUseAllowed()) {
                    revoke(type, update.resourceId(), AccessPermission.AI_USE);
                }
            }
            audits.recordAction(
                    actor, ownerTeacherId, "POLICY_UPDATE", type,
                    update.resourceId(), null, null, true);
            return policy;
        }).toList();
    }

    public List<ResourceAccessPolicy> listOwned(Long ownerTeacherId, ResourceType type) {
        return policies.selectList(new LambdaQueryWrapper<ResourceAccessPolicy>()
                .eq(ResourceAccessPolicy::getOwnerTeacherId, ownerTeacherId)
                .eq(ResourceAccessPolicy::getResourceType, type.name())
                .orderByDesc(ResourceAccessPolicy::getResourceId));
    }

    private void revoke(ResourceType type, Long resourceId, AccessPermission permission) {
        List<ResourceAccessGrant> active = grants.selectList(new LambdaQueryWrapper<ResourceAccessGrant>()
                .eq(ResourceAccessGrant::getResourceType, type.name())
                .eq(ResourceAccessGrant::getResourceId, resourceId)
                .eq(permission != null, ResourceAccessGrant::getPermissionType,
                        permission == null ? null : permission.name())
                .isNull(ResourceAccessGrant::getRevokedAt)
                .gt(ResourceAccessGrant::getExpiresAt, LocalDateTime.now()));
        LocalDateTime now = LocalDateTime.now();
        active.forEach(grant -> {
            grant.setRevokedAt(now);
            grant.setRevokedByTeacherId(grant.getOwnerTeacherId());
            grants.updateById(grant);
        });
    }

    public record PolicyUpdate(
            String resourceType,
            Long resourceId,
            boolean viewAllowed,
            boolean downloadAllowed,
            boolean aiUseAllowed) {
    }
}
