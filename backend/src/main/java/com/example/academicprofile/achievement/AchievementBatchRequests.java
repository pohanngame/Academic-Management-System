package com.example.academicprofile.achievement;

import java.util.List;

public final class AchievementBatchRequests {
    private AchievementBatchRequests() {}

    public record Reorder(List<Long> orderedIds) {}
    public record Update(
            List<Long> ids,
            Boolean isPublic,
            Boolean viewAllowed,
            Boolean downloadAllowed,
            Boolean aiUseAllowed) {}
    public record Delete(List<Long> ids) {}
}
