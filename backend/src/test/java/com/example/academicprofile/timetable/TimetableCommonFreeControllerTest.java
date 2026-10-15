package com.example.academicprofile.timetable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.example.academicprofile.common.ApiResponse;
import com.example.academicprofile.common.exception.BusinessException;
import com.example.academicprofile.organization.OrganizationService;
import com.example.academicprofile.timetable.TimetableDtos.CommonFreeResponse;

/** teacherIds 是逗号分隔字符串，需正确切分且不接受非数字。 */
class TimetableCommonFreeControllerTest {

    private final TimetableCommonFreeService service = Mockito.mock(TimetableCommonFreeService.class);
    private final TimetableCommonFreeExcelService excelService =
            Mockito.mock(TimetableCommonFreeExcelService.class);
    private final OrganizationService organizationService = Mockito.mock(OrganizationService.class);
    private final TimetableCommonFreeController controller =
            new TimetableCommonFreeController(service, excelService, organizationService);

    private static CommonFreeResponse empty() {
        return new CommonFreeResponse("2026-2027-1", 5, false, List.of(), List.of());
    }

    @Test
    void commaSeparatedTeacherIdsAreParsedAndForwarded() {
        when(service.query(eq("2026-2027-1"), eq(5), eq(null), any())).thenReturn(empty());

        ApiResponse<CommonFreeResponse> response =
                controller.commonFree("2026-2027-1", 5, null, " 7, 8 ,9 ");

        assertThat(response.success()).isTrue();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> captured = ArgumentCaptor.forClass(List.class);
        Mockito.verify(service).query(eq("2026-2027-1"), eq(5), eq(null), captured.capture());
        assertThat(captured.getValue()).containsExactly(7L, 8L, 9L);
    }

    @Test
    void blankTeacherIdsBecomesEmptyList() {
        when(service.query(any(), eq(5), any(), any())).thenReturn(empty());

        controller.commonFree("2026-2027-1", 5, 3L, "   ");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> captured = ArgumentCaptor.forClass(List.class);
        Mockito.verify(service).query(any(), eq(5), eq(3L), captured.capture());
        assertThat(captured.getValue()).isEmpty();
    }

    @Test
    void nonNumericTeacherIdsAreRejected() {
        assertThatThrownBy(() -> controller.commonFree("2026-2027-1", 5, null, "7,abc"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("comma separated");
    }
}
