package com.example.academicprofile.achievement;

import java.util.HashSet;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.academicprofile.access.ResourcePolicyService;
import com.example.academicprofile.common.exception.BusinessException;
import com.example.academicprofile.file.FileStorageService;
import com.example.academicprofile.security.CurrentUser;

@Service
public class AchievementMutationService {
    private static final int MAX_BATCH = 100;
    private final JdbcTemplate jdbc;
    private final ResourcePolicyService policies;
    private final FileStorageService files;

    public AchievementMutationService(JdbcTemplate jdbc, ResourcePolicyService policies, FileStorageService files) {
        this.jdbc = jdbc;
        this.policies = policies;
        this.files = files;
    }

    @Transactional
    public void reorder(CurrentUser actor, AchievementRecordType type, List<Long> orderedIds) {
        List<Long> ids = validIds(orderedIds);
        List<Long> owned = lockedOwnedIds(actor.teacherId(), type);
        if (owned.size() != ids.size() || !new HashSet<>(owned).equals(new HashSet<>(ids))) {
            throw new BusinessException("排序记录必须完整且全部属于当前教师");
        }
        for (int index = 0; index < ids.size(); index++) {
            jdbc.update("UPDATE " + type.table() + " SET sort_order=? WHERE id=? AND teacher_id=?",
                    index + 1, ids.get(index), actor.teacherId());
        }
    }

    @Transactional
    public void update(CurrentUser actor, AchievementRecordType type, AchievementBatchRequests.Update request) {
        List<Long> ids = validIds(request.ids());
        requireOwned(actor.teacherId(), type, ids);
        if (request.isPublic() == null && request.viewAllowed() == null
                && request.downloadAllowed() == null && request.aiUseAllowed() == null) {
            throw new BusinessException("请至少选择一项要修改的内容");
        }
        if (request.isPublic() != null) {
            updatePublic(actor.teacherId(), type, ids, request.isPublic());
        }
        if (request.viewAllowed() != null || request.downloadAllowed() != null || request.aiUseAllowed() != null) {
            policies.updateOwnedPartial(actor, type.resourceType(), ids,
                    request.viewAllowed(), request.downloadAllowed(), request.aiUseAllowed());
        }
    }

    @Transactional
    public void delete(CurrentUser actor, AchievementRecordType type, List<Long> rawIds) {
        List<Long> ids = validIds(rawIds);
        requireOwned(actor.teacherId(), type, ids);
        for (Long id : ids) {
            if (type.fileBusinessType() != null) {
                files.retireBusinessFiles(actor, type.fileBusinessType(), id);
            }
            policies.retireOwned(actor, type.resourceType(), id);
            if (jdbc.update("DELETE FROM " + type.table() + " WHERE id=? AND teacher_id=?", id, actor.teacherId()) != 1) {
                throw new BusinessException("删除成果失败");
            }
        }
    }

    private List<Long> lockedOwnedIds(Long teacherId, AchievementRecordType type) {
        return jdbc.queryForList("SELECT id FROM " + type.table()
                + " WHERE teacher_id=? ORDER BY sort_order ASC, id DESC FOR UPDATE", Long.class, teacherId);
    }

    private void requireOwned(Long teacherId, AchievementRecordType type, List<Long> ids) {
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        Object[] args = new Object[ids.size() + 1];
        args[0] = teacherId;
        for (int i = 0; i < ids.size(); i++) args[i + 1] = ids.get(i);
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + type.table()
                + " WHERE teacher_id=? AND id IN (" + placeholders + ")", Integer.class, args);
        if (count == null || count != ids.size()) throw new BusinessException("记录不存在或无权操作");
    }

    private void updatePublic(Long teacherId, AchievementRecordType type, List<Long> ids, boolean value) {
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        Object[] args = new Object[ids.size() + 2];
        args[0] = value;
        args[1] = teacherId;
        for (int i = 0; i < ids.size(); i++) args[i + 2] = ids.get(i);
        jdbc.update("UPDATE " + type.table() + " SET is_public=? WHERE teacher_id=? AND id IN ("
                + placeholders + ")", args);
    }

    private List<Long> validIds(List<Long> raw) {
        if (raw == null || raw.isEmpty() || raw.size() > MAX_BATCH || raw.stream().anyMatch(id -> id == null || id < 1)
                || new HashSet<>(raw).size() != raw.size()) {
            throw new BusinessException("操作必须包含 1-100 个不重复的有效记录");
        }
        return List.copyOf(raw);
    }
}
