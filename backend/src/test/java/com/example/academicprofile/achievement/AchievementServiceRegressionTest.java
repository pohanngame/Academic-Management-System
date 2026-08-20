package com.example.academicprofile.achievement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.academicprofile.access.ResourcePolicyService;
import com.example.academicprofile.publication.PaperRatingService;

class AchievementServiceRegressionTest {

    @Test
    void frontendRecordTypeNamesMapToTheFixedBackendWhitelist() {
        assertThat(AchievementRecordType.fromPath("projects")).isEqualTo(AchievementRecordType.PROJECTS);
        assertThat(AchievementRecordType.fromPath("teachingCourses")).isEqualTo(AchievementRecordType.TEACHING_COURSES);
        assertThat(AchievementRecordType.fromPath("papers")).isEqualTo(AchievementRecordType.PAPERS);
        assertThat(AchievementRecordType.fromPath("patents")).isEqualTo(AchievementRecordType.PATENTS);
        assertThat(AchievementRecordType.fromPath("certificates")).isEqualTo(AchievementRecordType.CERTIFICATES);
    }

    @Test
    void newProjectAppendsAfterExistingZeroSortedRecords() {
        ProjectMapper projects = mock(ProjectMapper.class);
        Project existing = new Project();
        existing.setId(1L);
        existing.setTeacherId(7L);
        existing.setSortOrder(0);
        when(projects.selectList(any())).thenReturn(List.of(existing));
        when(projects.insert(any(Project.class))).thenAnswer(invocation -> {
            ((Project) invocation.getArgument(0)).setId(2L);
            return 1;
        });

        AchievementService service = new AchievementService(
                projects,
                mock(TeachingCourseMapper.class),
                mock(PaperMapper.class),
                mock(PatentMapper.class),
                mock(CertificateMapper.class),
                mock(ResourcePolicyService.class),
                mock(PaperRatingService.class));

        Project created = service.createProject(7L, new ProjectRequest(
                "New project", null, null, null, null, null, null, null, 0, true));

        assertThat(created.getSortOrder()).isEqualTo(1);
    }
}
