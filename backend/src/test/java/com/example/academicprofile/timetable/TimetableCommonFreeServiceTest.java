package com.example.academicprofile.timetable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.example.academicprofile.common.exception.BusinessException;
import com.example.academicprofile.organization.Major;
import com.example.academicprofile.organization.MajorMapper;
import com.example.academicprofile.organization.TeacherMajor;
import com.example.academicprofile.organization.TeacherMajorMapper;
import com.example.academicprofile.teacher.TeacherProfile;
import com.example.academicprofile.teacher.TeacherProfileMapper;
import com.example.academicprofile.timetable.TimetableDtos.CommonFreeResponse;
import com.example.academicprofile.timetable.TimetableDtos.CommonFreeSlot;

class TimetableCommonFreeServiceTest {

    private TimetableImportBatchMapper batches;
    private TimetableCellMapper cells;
    private TimetableImportTeacherMapper importTeachers;
    private TimetableTeacherLinkMapper links;
    private TeacherMajorMapper teacherMajors;
    private MajorMapper majors;
    private TeacherProfileMapper teachers;
    private TimetableCommonFreeService service;

    @BeforeEach
    void setUp() {
        batches = Mockito.mock(TimetableImportBatchMapper.class);
        cells = Mockito.mock(TimetableCellMapper.class);
        importTeachers = Mockito.mock(TimetableImportTeacherMapper.class);
        links = Mockito.mock(TimetableTeacherLinkMapper.class);
        teacherMajors = Mockito.mock(TeacherMajorMapper.class);
        majors = Mockito.mock(MajorMapper.class);
        teachers = Mockito.mock(TeacherProfileMapper.class);
        service = new TimetableCommonFreeService(
                batches, cells, importTeachers, links, teacherMajors, majors, teachers);
    }

    private static TimetableImportBatch batch(long id, String semesterCode) {
        TimetableImportBatch batch = new TimetableImportBatch();
        batch.setId(id);
        batch.setSemesterCode(semesterCode);
        return batch;
    }

    private static TimetableCell cell(Long teacherId, int day, String slot, int weekStart, int weekEnd) {
        TimetableCell cell = new TimetableCell();
        cell.setBatchId(1L);
        cell.setTeacherId(teacherId);
        cell.setDayOfWeek(day);
        cell.setSlotCode(slot);
        cell.setWeekStart(weekStart);
        cell.setWeekEnd(weekEnd);
        cell.setCourseName("课程");
        cell.setRoomName("教一101");
        cell.setOccupancyText("课程\n班级\n(" + weekStart + "-" + weekEnd + "周)\n教一101");
        return cell;
    }

    private void teacherExists(Long id, String displayName) {
        TeacherProfile profile = new TeacherProfile();
        profile.setId(id);
        profile.setDisplayName(displayName);
        when(teachers.selectBatchIds(any())).thenReturn(List.of(profile));
    }

    private void mapped(Long teacherId, String sourceName) {
        TimetableTeacherLink link = new TimetableTeacherLink();
        link.setSourceName(sourceName);
        link.setTeacherId(teacherId);
        when(links.selectList(any())).thenReturn(List.of(link));
    }

    /** 教师出现在本次导入中（timetable_import_teacher 有记录）。 */
    private void imported(Long... teacherIds) {
        List<TimetableImportTeacher> rows = new java.util.ArrayList<>();
        for (Long teacherId : teacherIds) {
            TimetableImportTeacher row = new TimetableImportTeacher();
            row.setBatchId(1L);
            row.setTeacherId(teacherId);
            rows.add(row);
        }
        when(importTeachers.selectList(any())).thenReturn(rows);
    }

    @Test
    void slotIsFreeWhenNoSelectedTeacherOccupiesIt() {
        teacherExists(7L, "张三");
        mapped(7L, "张三");
        imported(7L);
        when(batches.selectList(any())).thenReturn(List.of(batch(1L, "2026-2027-1")));
        when(cells.selectList(any())).thenReturn(List.of(cell(7L, 1, "0102", 2, 13)));

        CommonFreeResponse response = service.query("2026-2027-1", 5, null, List.of(7L));

        assertThat(response.missingImport()).isFalse();
        assertThat(response.slots()).hasSize(42);
        CommonFreeSlot occupied = slot(response, 1, "0102");
        assertThat(occupied.free()).isFalse();
        assertThat(occupied.occupancies()).hasSize(1);
        assertThat(occupied.occupancies().get(0).teacherDisplayName()).isEqualTo("张三");
        // 同一天其它节次组仍空闲。
        assertThat(slot(response, 1, "0304").free()).isTrue();
    }

    @Test
    void slotIsNotFreeWhenAnySelectedTeacherOccupiesIt() {
        TeacherProfile first = new TeacherProfile();
        first.setId(7L);
        first.setDisplayName("张三");
        TeacherProfile second = new TeacherProfile();
        second.setId(8L);
        second.setDisplayName("李四");
        when(teachers.selectBatchIds(any())).thenReturn(List.of(first, second));

        TimetableTeacherLink linkA = new TimetableTeacherLink();
        linkA.setSourceName("张三");
        linkA.setTeacherId(7L);
        TimetableTeacherLink linkB = new TimetableTeacherLink();
        linkB.setSourceName("李四");
        linkB.setTeacherId(8L);
        when(links.selectList(any())).thenReturn(List.of(linkA, linkB));
        imported(7L, 8L);
        when(batches.selectList(any())).thenReturn(List.of(batch(1L, "2026-2027-1")));
        // 只有李四在周三 0506 有课：该格对所选两人都不空闲。
        when(cells.selectList(any())).thenReturn(List.of(cell(8L, 3, "0506", 1, 16)));

        CommonFreeResponse response =
                service.query("2026-2027-1", 3, null, List.of(7L, 8L));

        assertThat(slot(response, 3, "0506").free()).isFalse();
        assertThat(slot(response, 3, "0506").occupancies())
                .singleElement()
                .satisfies(entry -> assertThat(entry.teacherDisplayName()).isEqualTo("李四"));
        assertThat(slot(response, 3, "0708").free()).isTrue();
    }

    @Test
    void missingImportReturnsMissingAndAllSlotsNotFree() {
        teacherExists(7L, "张三");
        mapped(7L, "张三");
        when(batches.selectList(any())).thenReturn(List.of());

        CommonFreeResponse response = service.query("2099-2100-1", 5, null, List.of(7L));

        assertThat(response.missingImport()).isTrue();
        assertThat(response.semesterCode()).isEqualTo("2099-2100-1");
        // 缺数据时仍返回 42 项，但全部 free=false：不能当成空闲。
        assertThat(response.slots()).hasSize(42);
        assertThat(response.slots()).allSatisfy(slot -> assertThat(slot.free()).isFalse());
        // 已映射但无批次可供比对 → 不能算空闲。
        assertThat(response.excludedTeachers())
                .extracting(entry -> entry.reason())
                .containsExactly("ABSENT_FROM_IMPORT");
    }

    @Test
    void majorScopeAlsoExcludesUnmappedTeachers() {
        Major major = new Major();
        major.setId(3L);
        major.setName("软件工程");
        when(majors.selectById(3L)).thenReturn(major);
        TeacherMajor first = new TeacherMajor();
        first.setTeacherId(7L);
        TeacherMajor second = new TeacherMajor();
        second.setTeacherId(8L);
        when(teacherMajors.selectList(any())).thenReturn(List.of(first, second));

        TeacherProfile mappedProfile = new TeacherProfile();
        mappedProfile.setId(7L);
        mappedProfile.setDisplayName("张三");
        TeacherProfile unmappedProfile = new TeacherProfile();
        unmappedProfile.setId(8L);
        unmappedProfile.setDisplayName("李四");
        when(teachers.selectBatchIds(any())).thenReturn(List.of(mappedProfile, unmappedProfile));
        // 专业里有两位教师，只有张三是已映射的。
        mapped(7L, "张三");
        imported(7L);
        when(batches.selectList(any())).thenReturn(List.of(batch(1L, "2026-2027-1")));
        when(cells.selectList(any())).thenReturn(List.of(cell(7L, 2, "0304", 1, 16)));

        CommonFreeResponse response = service.query("2026-2027-1", 5, 3L, null);

        assertThat(response.excludedTeachers())
                .extracting(entry -> entry.sourceName())
                .containsExactly("李四");
        assertThat(response.excludedTeachers().get(0).reason()).isEqualTo("NO_TIMETABLE_MAPPING");
        // 李四不参与判断：张三的占用仍生效。
        assertThat(slot(response, 2, "0304").free()).isFalse();
    }

    @Test
    void unmappedTeachersAreExcludedFromTheIntersection() {
        TeacherProfile mapped = new TeacherProfile();
        mapped.setId(7L);
        mapped.setDisplayName("张三");
        TeacherProfile unmapped = new TeacherProfile();
        unmapped.setId(9L);
        unmapped.setDisplayName("王五");
        when(teachers.selectBatchIds(any())).thenReturn(List.of(mapped, unmapped));
        // 只有张三有课表映射；王五没有映射。
        mapped(7L, "张三");
        imported(7L);
        when(batches.selectList(any())).thenReturn(List.of(batch(1L, "2026-2027-1")));
        when(cells.selectList(any())).thenReturn(List.of());

        CommonFreeResponse response = service.query("2026-2027-1", 5, null, List.of(7L, 9L));

        assertThat(response.excludedTeachers())
                .extracting(entry -> entry.sourceName())
                .containsExactly("王五");
        assertThat(response.excludedTeachers().get(0).reason()).isEqualTo("NO_TIMETABLE_MAPPING");
        // 王五未参与判断：所有格子仍按张三的结果（此处无课）为空间。
        assertThat(response.slots()).allSatisfy(slot -> assertThat(slot.free()).isTrue());
    }

    @Test
    void majorOrTeacherIdsIsExactlyOneRequired() {
        assertThatThrownBy(() -> service.query("2026-2027-1", 5, null, null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.query("2026-2027-1", 5, null, List.of()))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.query("2026-2027-1", 5, 3L, List.of(7L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("exactly one");
    }

    @Test
    void weekAndSemesterAreValidated() {
        assertThatThrownBy(() -> service.query("2026-2027-1", 0, null, List.of(7L)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.query("2026-2027-1", 31, null, List.of(7L)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.query("  ", 5, null, List.of(7L)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void majorScopeResolvesItsTeachers() {
        Major major = new Major();
        major.setId(3L);
        major.setName("软件工程");
        when(majors.selectById(3L)).thenReturn(major);
        TeacherMajor relation = new TeacherMajor();
        relation.setTeacherId(7L);
        relation.setMajorId(3L);
        when(teacherMajors.selectList(any())).thenReturn(List.of(relation));
        teacherExists(7L, "张三");
        mapped(7L, "张三");
        imported(7L);
        when(batches.selectList(any())).thenReturn(List.of(batch(1L, "2026-2027-1")));
        when(cells.selectList(any())).thenReturn(List.of(cell(7L, 5, "0910", 1, 30)));

        CommonFreeResponse response = service.query("2026-2027-1", 10, 3L, null);

        assertThat(slot(response, 5, "0910").free()).isFalse();
        assertThat(response.slots()).hasSize(42);
    }

    @Test
    void unknownMajorOrTeacherFails() {
        when(majors.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.query("2026-2027-1", 5, 99L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Major not found");

        when(teachers.selectBatchIds(any())).thenReturn(List.of());
        assertThatThrownBy(() -> service.query("2026-2027-1", 5, null, List.of(123L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Teacher profile not found");
    }

    /**
     * 已映射但本学期导入记录里没有该教师 → ABSENT_FROM_IMPORT，不参与空闲判断。
     * 不能把他当成全学期有空。
     */
    @Test
    void mappedButAbsentFromImportIsExcluded() {
        TeacherProfile mappedProfile = new TeacherProfile();
        mappedProfile.setId(7L);
        mappedProfile.setDisplayName("张三");
        when(teachers.selectBatchIds(any())).thenReturn(List.of(mappedProfile));
        mapped(7L, "张三");
        // 有映射，但本批次没有任何导入记录。
        when(importTeachers.selectList(any())).thenReturn(List.of());
        when(batches.selectList(any())).thenReturn(List.of(batch(1L, "2026-2027-1")));
        when(cells.selectList(any())).thenReturn(List.of());

        CommonFreeResponse response = service.query("2026-2027-1", 5, null, List.of(7L));

        assertThat(response.excludedTeachers())
                .extracting(entry -> entry.reason())
                .containsExactly("ABSENT_FROM_IMPORT");
        assertThat(response.excludedTeachers().get(0).sourceName()).isEqualTo("张三");
        // 被排除的教师不参与判断，因此没有占用产生。
        assertThat(response.slots()).hasSize(42);
        assertThat(response.slots()).allSatisfy(slot -> assertThat(slot.occupancies()).isEmpty());
    }

    /**
     * 有导入记录但没有单元格 → 参与判断，该教师算空闲。
     * 这是“整学期无课”与“未出现在导入中”的关键区别。
     */
    @Test
    void importedTeacherWithoutCellsCountsAsFree() {
        TeacherProfile profile = new TeacherProfile();
        profile.setId(7L);
        profile.setDisplayName("张三");
        when(teachers.selectBatchIds(any())).thenReturn(List.of(profile));
        mapped(7L, "张三");
        // 出现在导入中，但整学期没有任何单元格。
        imported(7L);
        when(batches.selectList(any())).thenReturn(List.of(batch(1L, "2026-2027-1")));
        when(cells.selectList(any())).thenReturn(List.of());

        CommonFreeResponse response = service.query("2026-2027-1", 5, null, List.of(7L));

        // 有导入记录 → 不被排除。
        assertThat(response.excludedTeachers()).isEmpty();
        // 参与判断且无占用 → 42 格全部空闲。
        assertThat(response.slots()).hasSize(42);
        assertThat(response.slots()).allSatisfy(slot -> assertThat(slot.free()).isTrue());
    }

    @Test
    void slotsCoverEveryDayAndSlotCodeInFixedOrder() {
        teacherExists(7L, "张三");
        mapped(7L, "张三");
        imported(7L);
        when(batches.selectList(any())).thenReturn(List.of(batch(1L, "2026-2027-1")));
        when(cells.selectList(any())).thenReturn(List.of());

        List<CommonFreeSlot> slots = service.query("2026-2027-1", 5, null, List.of(7L)).slots();

        assertThat(slots).hasSize(42);
        assertThat(slots).allSatisfy(slot -> assertThat(slot.free()).isTrue());
        assertThat(slots.get(0).dayOfWeek()).isEqualTo(1);
        assertThat(slots.get(0).slotCode()).isEqualTo("0102");
        assertThat(slots.get(41).dayOfWeek()).isEqualTo(7);
        assertThat(slots.get(41).slotCode()).isEqualTo("1112");
    }

    private static CommonFreeSlot slot(CommonFreeResponse response, int dayOfWeek, String slotCode) {
        return response.slots().stream()
                .filter(slot -> slot.dayOfWeek() == dayOfWeek && slotCode.equals(slot.slotCode()))
                .findFirst()
                .orElseThrow();
    }
}
