package com.example.academicprofile.timetable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

import com.example.academicprofile.admin.AdminAuditService;
import com.example.academicprofile.common.exception.BusinessException;
import com.example.academicprofile.teacher.TeacherProfile;
import com.example.academicprofile.teacher.TeacherProfileService;
import com.example.academicprofile.timetable.TimetableDtos.TeacherLinkRequest;
import com.example.academicprofile.timetable.TimetableDtos.TeacherLinkResponse;

class TimetableTeacherLinkServiceTest {

    private TimetableTeacherLinkMapper links;
    private TeacherProfileService teachers;
    private AdminAuditService audit;
    private TimetableTeacherLinkService service;

    @BeforeEach
    void setUp() {
        links = Mockito.mock(TimetableTeacherLinkMapper.class);
        teachers = Mockito.mock(TeacherProfileService.class);
        audit = Mockito.mock(AdminAuditService.class);
        service = new TimetableTeacherLinkService(links, teachers, audit);
    }

    private static TeacherProfile profile(Long id, String name) {
        TeacherProfile p = new TeacherProfile();
        p.setId(id);
        p.setDisplayName(name);
        return p;
    }

    @Test
    void createsLinkWhenNeitherNameNorTeacherIsTaken() {
        when(teachers.getByTeacherId(7L)).thenReturn(profile(7L, "张三"));
        when(links.selectOne(any())).thenReturn(null);

        TeacherLinkResponse response =
                service.upsert(1L, new TeacherLinkRequest("张三", 7L));

        assertThat(response.sourceName()).isEqualTo("张三");
        assertThat(response.teacherId()).isEqualTo(7L);
        assertThat(response.teacherDisplayName()).isEqualTo("张三");
        verify(links).insert(any(TimetableTeacherLink.class));
    }

    @Test
    void rejectsNonexistentTeacher() {
        // 不存在的教师：TeacherProfileService 抛 404，映射不得落库。
        when(teachers.getByTeacherId(999L)).thenThrow(
                new BusinessException(HttpStatus.NOT_FOUND, "Teacher profile not found"));

        assertThatThrownBy(() -> service.upsert(1L, new TeacherLinkRequest("张三", 999L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Teacher profile not found");

        verify(links, never()).insert(any(TimetableTeacherLink.class));
    }

    @Test
    void rejectsTeacherAlreadyLinkedToAnotherName() {
        when(teachers.getByTeacherId(7L)).thenReturn(profile(7L, "张三"));
        TimetableTeacherLink other = new TimetableTeacherLink();
        other.setId(5L);
        other.setSourceName("李四");
        other.setTeacherId(7L);
        when(links.selectOne(any())).thenReturn(other);

        assertThatThrownBy(() -> service.upsert(1L, new TeacherLinkRequest("张三", 7L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already linked");

        verify(links, never()).insert(any(TimetableTeacherLink.class));
        verify(links, never()).updateById(any(TimetableTeacherLink.class));
    }

    @Test
    void upsertSameNameSameTeacherIsIdempotentUpdate() {
        when(teachers.getByTeacherId(7L)).thenReturn(profile(7L, "张三"));
        TimetableTeacherLink existing = new TimetableTeacherLink();
        existing.setId(5L);
        existing.setSourceName("张三");
        existing.setTeacherId(7L);
        when(links.selectOne(any())).thenReturn(existing);

        TeacherLinkResponse response =
                service.upsert(1L, new TeacherLinkRequest("张三", 7L));

        assertThat(response.id()).isEqualTo(5L);
        verify(links).updateById(any(TimetableTeacherLink.class));
        verify(links, never()).insert(any(TimetableTeacherLink.class));
    }

    @Test
    void rejectsBlankSourceName() {
        assertThatThrownBy(() -> service.upsert(1L, new TeacherLinkRequest("   ", 7L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("must not be blank");

        verify(links, never()).insert(any(TimetableTeacherLink.class));
    }

    @Test
    void deleteRejectsUnknownName() {
        when(links.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.delete(1L, "不存在"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void listReturnsDisplayNames() {
        TimetableTeacherLink row = new TimetableTeacherLink();
        row.setId(1L);
        row.setSourceName("张三");
        row.setTeacherId(7L);
        when(links.selectList(any())).thenReturn(List.of(row));
        when(teachers.getByTeacherId(7L)).thenReturn(profile(7L, "张三"));

        List<TeacherLinkResponse> responses = service.list();

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).teacherDisplayName()).isEqualTo("张三");
    }
}
