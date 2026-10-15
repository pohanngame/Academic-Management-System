package com.example.academicprofile.timetable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.example.academicprofile.common.exception.BusinessException;
import com.example.academicprofile.organization.Major;
import com.example.academicprofile.organization.MajorMapper;
import com.example.academicprofile.organization.TeacherMajor;
import com.example.academicprofile.organization.TeacherMajorMapper;
import com.example.academicprofile.teacher.TeacherProfile;
import com.example.academicprofile.teacher.TeacherProfileMapper;
import com.example.academicprofile.timetable.KbtableParser;
import com.example.academicprofile.timetable.TimetableDtos.CommonFreeResponse;
import com.example.academicprofile.timetable.TimetableDtos.CommonFreeSlot;
import com.example.academicprofile.timetable.TimetableDtos.ExcludedTeacher;
import com.example.academicprofile.timetable.TimetableDtos.OccupancyEntry;

/**
 * 公共空闲查询：所选教师在该学期、该周、该天、该节次组都没有占用才算空闲。
 * 只读已落库的课表快照，不接触教务。
 */
@Service
public class TimetableCommonFreeService {

    private static final int DAYS_PER_WEEK = 7;

    private final TimetableImportBatchMapper batches;
    private final TimetableCellMapper cells;
    private final TimetableImportTeacherMapper importTeachers;
    private final TimetableTeacherLinkMapper links;
    private final TeacherMajorMapper teacherMajors;
    private final MajorMapper majors;
    private final TeacherProfileMapper teachers;

    public TimetableCommonFreeService(
            TimetableImportBatchMapper batches,
            TimetableCellMapper cells,
            TimetableImportTeacherMapper importTeachers,
            TimetableTeacherLinkMapper links,
            TeacherMajorMapper teacherMajors,
            MajorMapper majors,
            TeacherProfileMapper teachers) {
        this.batches = batches;
        this.cells = cells;
        this.importTeachers = importTeachers;
        this.links = links;
        this.teacherMajors = teacherMajors;
        this.majors = majors;
        this.teachers = teachers;
    }

    public CommonFreeResponse query(String semesterCode, int week, Long majorId, List<Long> teacherIds) {
        String semester = requireSemester(semesterCode);
        requireWeek(week);
        // majorId 与 teacherIds 必须二选一。
        boolean hasMajor = majorId != null;
        boolean hasTeachers = teacherIds != null && !teacherIds.isEmpty();
        if (hasMajor == hasTeachers) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Provide exactly one of majorId or teacherIds");
        }

        Set<Long> ids = hasMajor ? byMajor(majorId) : byTeachers(teacherIds);
        Map<Long, TeacherProfile> profiles = loadProfiles(ids);

        TimetableImportBatch batch = latestBatch(semester);
        if (batch == null) {
            // 没有该学期数据：missingImport=true，42 格全部 free=false（缺数据不等于空闲）。
            // 无批次时无法判定 ABSENT_FROM_IMPORT，只能按是否映射分类。
            Set<Long> mapped = mappedTeacherIds();
            List<ExcludedTeacher> excluded = new ArrayList<>();
            for (Long id : ids) {
                excluded.add(new ExcludedTeacher(
                        displayName(profiles, id),
                        mapped.contains(id) ? "ABSENT_FROM_IMPORT" : "NO_TIMETABLE_MAPPING"));
            }
            return new CommonFreeResponse(semester, week, true, unavailableSlots(), excluded);
        }

        TargetSet classified = classify(ids, batch.getId(), profiles);
        Map<Long, List<TimetableCell>> byTeacher =
                loadCells(batch.getId(), classified.teacherIds(), week);
        return new CommonFreeResponse(
                semester, week, false,
                slotsFor(byTeacher, classified), classified.excluded());
    }

    private String requireSemester(String semesterCode) {
        if (!StringUtils.hasText(semesterCode)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "semesterCode is required");
        }
        return semesterCode.trim();
    }

    private void requireWeek(int week) {
        if (week < 1 || week > 30) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "week must be between 1 and 30");
        }
    }

    /**
     * 专业范围：包含该专业全部教师。
     */
    private Set<Long> byMajor(Long majorId) {
        Major major = majors.selectById(majorId);
        if (major == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Major not found");
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (TeacherMajor relation : teacherMajors.selectList(
                new LambdaQueryWrapper<TeacherMajor>().eq(TeacherMajor::getMajorId, majorId))) {
            ids.add(relation.getTeacherId());
        }
        return ids;
    }

    /**
     * 教师范围：只接受已存在的 teacher_profile；不存在的教师返回 404。
     */
    private Set<Long> byTeachers(List<Long> teacherIds) {
        Set<Long> ids = new LinkedHashSet<>(teacherIds);
        if (loadProfiles(ids).size() != ids.size()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Teacher profile not found");
        }
        return ids;
    }

    /** 已建立课表映射的教师（timetable_teacher_link 的 teacher_id）。 */
    private Set<Long> mappedTeacherIds() {
        Set<Long> mapped = new LinkedHashSet<>();
        for (TimetableTeacherLink link : links.selectList(null)) {
            mapped.add(link.getTeacherId());
        }
        return mapped;
    }

    /** 本次导入中出现过的教师（timetable_import_teacher 的 teacher_id）。 */
    private Set<Long> importedTeacherIds(Long batchId, Set<Long> ids) {
        Set<Long> imported = new LinkedHashSet<>();
        if (ids.isEmpty()) {
            return imported;
        }
        for (TimetableImportTeacher row : importTeachers.selectList(
                new LambdaQueryWrapper<TimetableImportTeacher>()
                        .eq(TimetableImportTeacher::getBatchId, batchId)
                        .in(TimetableImportTeacher::getTeacherId, ids))) {
            imported.add(row.getTeacherId());
        }
        return imported;
    }

    /**
     * 按“是否映射”与“是否出现在本次导入”划分教师：
     *
     * - 无课表映射 → NO_TIMETABLE_MAPPING，不参与判断。
     * - 已映射但本批次无导入记录 → ABSENT_FROM_IMPORT，不参与判断。
     * - 有导入记录 → 参与判断（即使整学期无单元格，也算空闲）。
     */
    private TargetSet classify(Set<Long> ids, Long batchId, Map<Long, TeacherProfile> profiles) {
        Set<Long> mapped = mappedTeacherIds();
        Set<Long> imported = importedTeacherIds(batchId, ids);
        List<ExcludedTeacher> excluded = new ArrayList<>();
        Set<Long> included = new LinkedHashSet<>();
        for (Long id : ids) {
            if (!mapped.contains(id)) {
                excluded.add(new ExcludedTeacher(displayName(profiles, id), "NO_TIMETABLE_MAPPING"));
            } else if (!imported.contains(id)) {
                excluded.add(new ExcludedTeacher(displayName(profiles, id), "ABSENT_FROM_IMPORT"));
            } else {
                included.add(id);
            }
        }
        return new TargetSet(included, excluded);
    }

    private String displayName(Map<Long, TeacherProfile> profiles, Long id) {
        TeacherProfile profile = profiles.get(id);
        return profile == null || profile.getDisplayName() == null
                ? String.valueOf(id)
                : profile.getDisplayName();
    }

    private Map<Long, TeacherProfile> loadProfiles(Set<Long> ids) {
        Map<Long, TeacherProfile> profiles = new LinkedHashMap<>();
        for (TeacherProfile profile : teachers.selectBatchIds(ids)) {
            profiles.put(profile.getId(), profile);
        }
        return profiles;
    }

    /** 同一 semesterCode 只保留最近一次批次（同步时已替换旧批次，这里取防御性最新）。 */
    private TimetableImportBatch latestBatch(String semesterCode) {
        List<TimetableImportBatch> found = batches.selectList(
                new LambdaQueryWrapper<TimetableImportBatch>()
                        .eq(TimetableImportBatch::getSemesterCode, semesterCode)
                        .orderByDesc(TimetableImportBatch::getId));
        return found.isEmpty() ? null : found.get(0);
    }

    /** 只取覆盖该周的单元格（week_start <= week <= week_end）。 */
    private Map<Long, List<TimetableCell>> loadCells(Long batchId, Set<Long> teacherIds, int week) {
        Map<Long, List<TimetableCell>> byTeacher = new LinkedHashMap<>();
        for (Long teacherId : teacherIds) {
            byTeacher.put(teacherId, new ArrayList<>());
        }
        List<TimetableCell> found = cells.selectList(
                new LambdaQueryWrapper<TimetableCell>()
                        .eq(TimetableCell::getBatchId, batchId)
                        .in(TimetableCell::getTeacherId, teacherIds)
                        .le(TimetableCell::getWeekStart, week)
                        .ge(TimetableCell::getWeekEnd, week));
        for (TimetableCell cell : found) {
            byTeacher.get(cell.getTeacherId()).add(cell);
        }
        return byTeacher;
    }

    private List<CommonFreeSlot> slotsFor(
            Map<Long, List<TimetableCell>> byTeacher, TargetSet target) {
        Map<Long, TeacherProfile> profiles = loadProfiles(target.teacherIds());
        List<CommonFreeSlot> slots = new ArrayList<>(DAYS_PER_WEEK * KbtableParser.SLOT_CODES.size());
        for (int day = 1; day <= DAYS_PER_WEEK; day++) {
            for (String slotCode : KbtableParser.SLOT_CODES) {
                List<OccupancyEntry> occupancies = new ArrayList<>();
                for (Long teacherId : target.teacherIds()) {
                    for (TimetableCell cell : byTeacher.get(teacherId)) {
                        if (cell.getDayOfWeek() == day && slotCode.equals(cell.getSlotCode())) {
                            TeacherProfile profile = profiles.get(teacherId);
                            occupancies.add(new OccupancyEntry(
                                    teacherId,
                                    profile == null ? null : profile.getDisplayName(),
                                    cell.getCourseName(),
                                    cell.getRoomName(),
                                    cell.getOccupancyText()));
                        }
                    }
                }
                // 任一教师占用则该格不空闲。
                slots.add(new CommonFreeSlot(day, slotCode, occupancies.isEmpty(), occupancies));
            }
        }
        return slots;
    }

    /** 无批次时 slots 仍固定 42 项，且全部 free=false（缺数据不等于空闲）。 */
    private List<CommonFreeSlot> unavailableSlots() {
        List<CommonFreeSlot> slots = new ArrayList<>(DAYS_PER_WEEK * KbtableParser.SLOT_CODES.size());
        for (int day = 1; day <= DAYS_PER_WEEK; day++) {
            for (String slotCode : KbtableParser.SLOT_CODES) {
                slots.add(new CommonFreeSlot(day, slotCode, false, List.of()));
            }
        }
        return slots;
    }

    /** 参与空闲判断的教师集合，以及被排除的教师。 */
    private record TargetSet(Set<Long> teacherIds, List<ExcludedTeacher> excluded) {
    }
}
