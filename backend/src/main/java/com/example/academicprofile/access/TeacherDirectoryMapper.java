package com.example.academicprofile.access;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TeacherDirectoryMapper {
    @Select("""
            <script>
            SELECT tp.id AS teacherId, tp.display_name AS displayName, tp.title,
                   m.id AS majorId, m.name AS majorName,
                   (SELECT GROUP_CONCAT(ara.role_code ORDER BY ara.role_code)
                    FROM academic_role_assignment ara
                    WHERE ara.user_id = u.id) AS roleCodes
            FROM teacher_profile tp
            JOIN `user` u ON u.id = tp.user_id
            LEFT JOIN teacher_major tm ON tm.teacher_id = tp.id
            LEFT JOIN major m ON m.id = tm.major_id
            WHERE u.role = 'TEACHER' AND u.status = 'ACTIVE'
            <if test="search != null and search != ''">
              AND (tp.display_name LIKE CONCAT('%', #{search}, '%')
                   OR m.name LIKE CONCAT('%', #{search}, '%'))
            </if>
            ORDER BY tp.display_name, tp.id
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<TeacherDirectoryItem> list(
            @Param("search") String search, @Param("offset") int offset, @Param("size") int size);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM teacher_profile tp
            JOIN `user` u ON u.id = tp.user_id
            LEFT JOIN teacher_major tm ON tm.teacher_id = tp.id
            LEFT JOIN major m ON m.id = tm.major_id
            WHERE u.role = 'TEACHER' AND u.status = 'ACTIVE'
            <if test="search != null and search != ''">
              AND (tp.display_name LIKE CONCAT('%', #{search}, '%')
                   OR m.name LIKE CONCAT('%', #{search}, '%'))
            </if>
            </script>
            """)
    long count(@Param("search") String search);

    @Select("""
            <script>
            SELECT tp.id AS teacherId, tp.display_name AS displayName, tp.title,
                   m.id AS majorId, m.name AS majorName,
                   (SELECT GROUP_CONCAT(ara.role_code ORDER BY ara.role_code)
                    FROM academic_role_assignment ara
                    WHERE ara.user_id = u.id) AS roleCodes
            FROM teacher_profile tp
            JOIN `user` u ON u.id = tp.user_id
            LEFT JOIN teacher_major tm ON tm.teacher_id = tp.id
            LEFT JOIN major m ON m.id = tm.major_id
            WHERE u.role = 'TEACHER' AND u.status = 'ACTIVE'
              AND tp.id &lt;&gt; #{actorTeacherId}
              AND EXISTS (
                SELECT 1
                FROM academic_role_assignment actor_role
                WHERE actor_role.user_id = #{actorUserId}
                  AND (
                    (actor_role.role_code = 'MAJOR_LEADER'
                     AND actor_role.major_id = m.id
                     AND m.active = 1
                     AND (SELECT COALESCE(MAX(CASE target_role.role_code
                         WHEN 'FULL_TIME_TEACHER' THEN 1
                         WHEN 'MAJOR_LEADER' THEN 2
                         WHEN 'VICE_DEAN' THEN 3
                         WHEN 'DEAN' THEN 4
                         WHEN 'PARTY_SECRETARY' THEN 4 ELSE 1 END), 1)
                          FROM academic_role_assignment target_role
                          WHERE target_role.user_id = u.id) &lt;= 1)
                    OR (actor_role.role_code = 'VICE_DEAN'
                        AND (SELECT COALESCE(MAX(CASE target_role.role_code
                            WHEN 'FULL_TIME_TEACHER' THEN 1
                            WHEN 'MAJOR_LEADER' THEN 2
                            WHEN 'VICE_DEAN' THEN 3
                            WHEN 'DEAN' THEN 4
                            WHEN 'PARTY_SECRETARY' THEN 4 ELSE 1 END), 1)
                             FROM academic_role_assignment target_role
                             WHERE target_role.user_id = u.id) &lt;= 2)
                    OR (actor_role.role_code IN ('DEAN', 'PARTY_SECRETARY')
                        AND (SELECT COALESCE(MAX(CASE target_role.role_code
                            WHEN 'FULL_TIME_TEACHER' THEN 1
                            WHEN 'MAJOR_LEADER' THEN 2
                            WHEN 'VICE_DEAN' THEN 3
                            WHEN 'DEAN' THEN 4
                            WHEN 'PARTY_SECRETARY' THEN 4 ELSE 1 END), 1)
                             FROM academic_role_assignment target_role
                             WHERE target_role.user_id = u.id) &lt;= 3)
                  )
              )
            <if test="search != null and search != ''">
              AND (tp.display_name LIKE CONCAT('%', #{search}, '%')
                   OR m.name LIKE CONCAT('%', #{search}, '%'))
            </if>
            ORDER BY tp.display_name, tp.id
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<TeacherDirectoryItem> listManaged(
            @Param("actorUserId") Long actorUserId,
            @Param("actorTeacherId") Long actorTeacherId,
            @Param("search") String search,
            @Param("offset") int offset,
            @Param("size") int size);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM teacher_profile tp
            JOIN `user` u ON u.id = tp.user_id
            LEFT JOIN teacher_major tm ON tm.teacher_id = tp.id
            LEFT JOIN major m ON m.id = tm.major_id
            WHERE u.role = 'TEACHER' AND u.status = 'ACTIVE'
              AND tp.id &lt;&gt; #{actorTeacherId}
              AND EXISTS (
                SELECT 1
                FROM academic_role_assignment actor_role
                WHERE actor_role.user_id = #{actorUserId}
                  AND (
                    (actor_role.role_code = 'MAJOR_LEADER'
                     AND actor_role.major_id = m.id
                     AND m.active = 1
                     AND (SELECT COALESCE(MAX(CASE target_role.role_code
                         WHEN 'FULL_TIME_TEACHER' THEN 1
                         WHEN 'MAJOR_LEADER' THEN 2
                         WHEN 'VICE_DEAN' THEN 3
                         WHEN 'DEAN' THEN 4
                         WHEN 'PARTY_SECRETARY' THEN 4 ELSE 1 END), 1)
                          FROM academic_role_assignment target_role
                          WHERE target_role.user_id = u.id) &lt;= 1)
                    OR (actor_role.role_code = 'VICE_DEAN'
                        AND (SELECT COALESCE(MAX(CASE target_role.role_code
                            WHEN 'FULL_TIME_TEACHER' THEN 1
                            WHEN 'MAJOR_LEADER' THEN 2
                            WHEN 'VICE_DEAN' THEN 3
                            WHEN 'DEAN' THEN 4
                            WHEN 'PARTY_SECRETARY' THEN 4 ELSE 1 END), 1)
                             FROM academic_role_assignment target_role
                             WHERE target_role.user_id = u.id) &lt;= 2)
                    OR (actor_role.role_code IN ('DEAN', 'PARTY_SECRETARY')
                        AND (SELECT COALESCE(MAX(CASE target_role.role_code
                            WHEN 'FULL_TIME_TEACHER' THEN 1
                            WHEN 'MAJOR_LEADER' THEN 2
                            WHEN 'VICE_DEAN' THEN 3
                            WHEN 'DEAN' THEN 4
                            WHEN 'PARTY_SECRETARY' THEN 4 ELSE 1 END), 1)
                             FROM academic_role_assignment target_role
                             WHERE target_role.user_id = u.id) &lt;= 3)
                  )
              )
            <if test="search != null and search != ''">
              AND (tp.display_name LIKE CONCAT('%', #{search}, '%')
                   OR m.name LIKE CONCAT('%', #{search}, '%'))
            </if>
            </script>
            """)
    long countManaged(
            @Param("actorUserId") Long actorUserId,
            @Param("actorTeacherId") Long actorTeacherId,
            @Param("search") String search);

    record TeacherDirectoryItem(
            Long teacherId, String displayName, String title, Long majorId, String majorName,
            String roleCodes) {
    }
}
