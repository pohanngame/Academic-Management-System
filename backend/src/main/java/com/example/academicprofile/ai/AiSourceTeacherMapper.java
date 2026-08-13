package com.example.academicprofile.ai;

import java.util.List;
import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiSourceTeacherMapper {

    @Select("""
            SELECT tp.id AS teacherId, tp.display_name AS displayName, m.name AS majorName,
                   COUNT(DISTINCT CASE WHEN g.resource_type <> 'FILE_ATTACHMENT'
                       THEN CONCAT(g.resource_type, ':', g.resource_id) END) AS availableResourceCount,
                   COUNT(DISTINCT CASE WHEN g.resource_type = 'FILE_ATTACHMENT'
                       THEN g.resource_id END) AS availableAttachmentCount
            FROM resource_access_grant g
            JOIN resource_access_policy p
              ON p.resource_type = g.resource_type AND p.resource_id = g.resource_id
             AND p.owner_teacher_id = g.owner_teacher_id
             AND p.view_allowed = 1 AND p.ai_use_allowed = 1
            JOIN teacher_profile tp ON tp.id = g.owner_teacher_id
            JOIN `user` u ON u.id = tp.user_id AND u.role = 'TEACHER' AND u.status = 'ACTIVE'
            LEFT JOIN teacher_major tm ON tm.teacher_id = tp.id
            LEFT JOIN major m ON m.id = tm.major_id
            WHERE g.requester_teacher_id = #{requesterTeacherId}
              AND g.permission_type = 'AI_USE'
              AND g.revoked_at IS NULL
              AND g.effective_at <= #{now}
              AND g.expires_at > #{now}
              AND (
                (g.resource_type = 'ACADEMIC_QUALIFICATION' AND EXISTS
                    (SELECT 1 FROM academic_qualification r WHERE r.id = g.resource_id AND r.teacher_id = g.owner_teacher_id))
                OR (g.resource_type = 'TEACHING_AREA' AND EXISTS
                    (SELECT 1 FROM teaching_area r WHERE r.id = g.resource_id AND r.teacher_id = g.owner_teacher_id))
                OR (g.resource_type = 'RESEARCH_AREA' AND EXISTS
                    (SELECT 1 FROM research_area r WHERE r.id = g.resource_id AND r.teacher_id = g.owner_teacher_id))
                OR (g.resource_type = 'PROFESSIONAL_SERVICE' AND EXISTS
                    (SELECT 1 FROM professional_service r WHERE r.id = g.resource_id AND r.teacher_id = g.owner_teacher_id))
                OR (g.resource_type = 'WORKING_EXPERIENCE' AND EXISTS
                    (SELECT 1 FROM working_experience r WHERE r.id = g.resource_id AND r.teacher_id = g.owner_teacher_id))
                OR (g.resource_type = 'PROJECT' AND EXISTS
                    (SELECT 1 FROM project r WHERE r.id = g.resource_id AND r.teacher_id = g.owner_teacher_id))
                OR (g.resource_type = 'TEACHING_COURSE' AND EXISTS
                    (SELECT 1 FROM teaching_course r WHERE r.id = g.resource_id AND r.teacher_id = g.owner_teacher_id))
                OR (g.resource_type = 'PAPER' AND EXISTS
                    (SELECT 1 FROM paper r WHERE r.id = g.resource_id AND r.teacher_id = g.owner_teacher_id))
                OR (g.resource_type = 'PATENT' AND EXISTS
                    (SELECT 1 FROM patent r WHERE r.id = g.resource_id AND r.teacher_id = g.owner_teacher_id))
                OR (g.resource_type = 'CERTIFICATE' AND EXISTS
                    (SELECT 1 FROM certificate r WHERE r.id = g.resource_id AND r.teacher_id = g.owner_teacher_id))
                OR (g.resource_type = 'FILE_ATTACHMENT' AND EXISTS (
                    SELECT 1
                    FROM file_metadata f
                    JOIN resource_access_policy parent_policy
                      ON parent_policy.resource_type = f.business_type
                     AND parent_policy.resource_id = f.business_id
                     AND parent_policy.owner_teacher_id = f.teacher_id
                     AND parent_policy.view_allowed = 1
                    WHERE f.id = g.resource_id AND f.teacher_id = g.owner_teacher_id
                      AND f.deleted = 0 AND f.business_type IN ('PAPER', 'PATENT', 'CERTIFICATE')
                      AND EXISTS (SELECT 1 FROM ocr_task o
                          WHERE o.file_id = f.id AND o.teacher_id = f.teacher_id
                            AND o.status = 'CONFIRMED' AND o.raw_text IS NOT NULL
                            AND TRIM(o.raw_text) <> '')
                ))
              )
            GROUP BY tp.id, tp.display_name, m.name
            ORDER BY tp.display_name, tp.id
            LIMIT #{limit}
            """)
    List<GrantedSourceTeacher> listGranted(
            @Param("requesterTeacherId") Long requesterTeacherId,
            @Param("now") LocalDateTime now,
            @Param("limit") int limit);

    record GrantedSourceTeacher(
            Long teacherId,
            String displayName,
            String majorName,
            long availableResourceCount,
            long availableAttachmentCount) {
    }
}
