package com.example.academicprofile.timetable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.example.academicprofile.admin.AdminAuditService;
import com.example.academicprofile.common.exception.BusinessException;
import com.example.academicprofile.timetable.TimetableDtos.CaptchaRequest;
import com.example.academicprofile.timetable.TimetableDtos.CaptchaResponse;
import com.example.academicprofile.timetable.TimetableDtos.SyncRequest;
import com.example.academicprofile.timetable.TimetableDtos.SyncResponse;
import com.example.academicprofile.timetable.TimetableSyncAttemptStore.JsxsdAuthPage;

class TimetableSyncServiceTest {

    private JsxsdClient jsxsd;
    private JsxsdPasswordCipher passwordCipher;
    private KbtableParser parser;
    private TimetableSyncAttemptStore attempts;
    private TimetableTeacherLinkMapper links;
    private TimetableImportBatchMapper batches;
    private TimetableCellMapper cells;
    private TimetableImportTeacherMapper importTeachers;
    private AdminAuditService audit;
    private TimetableSyncService service;

    private static final String USERNAME = "jg-account";
    private static final String PASSWORD = "super-secret-password";

    @BeforeEach
    void setUp() {
        jsxsd = Mockito.mock(JsxsdClient.class);
        passwordCipher = new JsxsdPasswordCipher(new java.security.SecureRandom());
        parser = new KbtableParser();
        attempts = new TimetableSyncAttemptStore();
        links = Mockito.mock(TimetableTeacherLinkMapper.class);
        batches = Mockito.mock(TimetableImportBatchMapper.class);
        cells = Mockito.mock(TimetableCellMapper.class);
        importTeachers = Mockito.mock(TimetableImportTeacherMapper.class);
        audit = Mockito.mock(AdminAuditService.class);
        service = new TimetableSyncService(
                jsxsd, passwordCipher, parser, attempts, links, batches, cells, importTeachers, audit);
        // 总表页与 kbjcmsid 是同步的必经前置步骤，默认给出可用值。
        when(jsxsd.fetchTeacherTablePage(any())).thenReturn("<select id=\"kbjcmsid\">"
                + "<option selected value=\"3\">节次模式</option></select>");
        when(jsxsd.extractKbjcmsid(anyString())).thenReturn("3");
    }

    /** 教务域的会话 Cookie，供 login(...) 的返回值使用。 */
    private static TimetableSyncAttemptStore.CookieJar jwCookies() {
        TimetableSyncAttemptStore.CookieJar jar = new TimetableSyncAttemptStore.CookieJar();
        jar.putAllFrom("jw.dgpt.edu.cn", Map.of("JSESSIONID", "jw"));
        return jar;
    }

    private JsxsdAuthPage authPage() {
        return new JsxsdAuthPage(
                "https://authserver.dgpt.edu.cn/authserver/login",
                "0123456789abcdef", "lt-value", "execution-value", new TimetableSyncAttemptStore.CookieJar());
    }

    /** 43 列的教师行：第 1 列 `<nobr>` 姓名，occupiedColumns（1..42）位置填課。 */
    private static String teacherRow(String name, int... occupiedColumns) {
        Set<Integer> occupied = new HashSet<>();
        for (int column : occupiedColumns) {
            occupied.add(column);
        }
        StringBuilder row = new StringBuilder("<tr><td><nobr>").append(name).append("</nobr></td>");
        for (int column = 1; column <= 42; column++) {
            row.append("<td>").append(occupied.contains(column)
                    ? "课程<br>班级<br>(2-13周)<br>教室"
                    : "&nbsp;").append("</td>");
        }
        return row.append("</tr>").toString();
    }

    /** 表头：第 1 行星期、第 2 行节次组，各 42 格。 */
    private static String headerRows() {
        StringBuilder days = new StringBuilder();
        StringBuilder slots = new StringBuilder();
        for (int day = 0; day < 7; day++) {
            for (String slot : KbtableParser.SLOT_CODES) {
                days.append("<td>星期").append(day + 1).append("</td>");
                slots.append("<td>").append(slot).append("</td>");
            }
        }
        return "<tr><td></td>" + days + "</tr><tr><td></td>" + slots + "</tr>";
    }

    private static String html(String... rows) {
        return "<table id=\"kbtable\"><tbody>" + headerRows() + String.join("", rows)
                + "</tbody></table>";
    }

    private void linkTeacher(String sourceName, Long teacherId) {
        TimetableTeacherLink link = new TimetableTeacherLink();
        link.setSourceName(sourceName);
        link.setTeacherId(teacherId);
        when(links.selectList(any())).thenReturn(List.of(link));
    }

    @Test
    void startAttemptReturnsAttemptIdAndCaptchaFlag() {
        when(jsxsd.openAuthPage()).thenReturn(authPage());
        when(jsxsd.checkCaptcha(any(), anyString()))
                .thenReturn(new JsxsdClient.CaptchaChallenge(true, "YmdpbWFnZQ==", "aW1hZ2U=", "c2FmZQ=="));

        CaptchaResponse response = service.startAttempt(1L, new CaptchaRequest(USERNAME));

        assertThat(response.attemptId()).isNotBlank();
        assertThat(response.captchaRequired()).isTrue();
        assertThat(response.sliderRequired()).isTrue();
        assertThat(response.sliderBackgroundBase64()).isEqualTo("YmdpbWFnZQ==");
        assertThat(response.sliderPieceBase64()).isEqualTo("aW1hZ2U=");
        // 不再返回旧的单图字段。
        assertThat(response.captchaImageBase64()).isNull();
    }

    @Test
    void captchaFailureLeavesNoBatchAndNoCells() {
        when(jsxsd.openAuthPage()).thenReturn(authPage());
        when(jsxsd.checkCaptcha(any(), anyString()))
                .thenReturn(new JsxsdClient.CaptchaChallenge(true, "YmdpbWFnZQ==", "aW1hZ2U=", "c2FmZQ=="));
        String attemptId = service.startAttempt(1L, new CaptchaRequest(USERNAME)).attemptId();

        // 教务以凭据/验证码错误拒绝：不产生任何批次或单元格。
        when(jsxsd.verifySliderCaptcha(any(), anyString(), any(), any(), any())).thenReturn(true);
        when(jsxsd.login(any(), anyString(), anyString(), any()))
                .thenThrow(new BusinessException(
                        org.springframework.http.HttpStatus.BAD_REQUEST, "rejected"));

        assertThatThrownBy(() -> service.sync(1L,
                new SyncRequest(attemptId, "2026-2027-1", PASSWORD, "0000", null, null, null)))
                .isInstanceOf(BusinessException.class);

        verify(batches, never()).insert(any(TimetableImportBatch.class));
        verify(cells, never()).insert(any(TimetableCell.class));
    }

    @Test
    void rejectedTicketExchangeStopsBeforeReadingTheTeacherTable() {
        when(jsxsd.openAuthPage()).thenReturn(authPage());
        when(jsxsd.checkCaptcha(any(), anyString()))
                .thenReturn(new JsxsdClient.CaptchaChallenge(true, "YmdpbWFnZQ==", "aW1hZ2U=", "c2FmZQ=="));
        String attemptId = service.startAttempt(1L, new CaptchaRequest(USERNAME)).attemptId();

        // 换票失败（HTTP 200 但是登录页）：绝不能继续去读总表页。
        when(jsxsd.verifySliderCaptcha(any(), anyString(), any(), any(), any())).thenReturn(true);
        when(jsxsd.login(any(), anyString(), anyString(), any()))
                .thenThrow(new BusinessException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Academic affairs rejected the credentials or captcha"));

        assertThatThrownBy(() -> service.sync(1L,
                new SyncRequest(attemptId, "2026-2027-1", PASSWORD, "0000", null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("rejected the credentials or captcha");

        verify(jsxsd, never()).fetchTeacherTablePage(any());
        verify(jsxsd, never()).fetchTeacherTable(any(), anyString(), anyString());
    }

    @Test
    void unmappedTeachersAreSkippedAndReportedWithoutFailingTheBatch() {
        when(jsxsd.openAuthPage()).thenReturn(authPage());
        when(jsxsd.checkCaptcha(any(), anyString()))
                .thenReturn(new JsxsdClient.CaptchaChallenge(false, null, null, null));
        String attemptId = service.startAttempt(1L, new CaptchaRequest(USERNAME)).attemptId();

        when(jsxsd.login(any(), anyString(), anyString(), any())).thenReturn(jwCookies());
        // 已映射「张三」有课；「李四」「王五」未映射：整批不失败，只报告未映射名单。
        when(jsxsd.fetchTeacherTable(any(), anyString(), anyString()))
                .thenReturn(html(teacherRow("张三", 1, 13),
                        teacherRow("李四", 1), teacherRow("王五", 7)));
        linkTeacher("张三", 7L);

        SyncResponse response = service.sync(1L,
                new SyncRequest(attemptId, "2026-2027-1", PASSWORD, null, null, null, null));

        assertThat(response.unmappedNames()).containsExactly("李四", "王五");
        assertThat(response.teacherCount()).isEqualTo(1);
        // 只保存已映射教师的两个非空单元格。
        assertThat(response.cellCount()).isEqualTo(2);
        verify(batches).insert(any(TimetableImportBatch.class));

        ArgumentCaptor<TimetableCell> saved = ArgumentCaptor.forClass(TimetableCell.class);
        verify(cells, org.mockito.Mockito.times(2)).insert(saved.capture());
        assertThat(saved.getAllValues())
                .allSatisfy(cell -> assertThat(cell.getTeacherId()).isEqualTo(7L));

        // 未映射姓名不得入库。
        String persisted = saved.getAllValues().stream()
                .map(TimetableCell::getOccupancyText)
                .reduce("", (a, b) -> a + b);
        assertThat(persisted).doesNotContain("李四").doesNotContain("王五");
    }

    @Test
    void kbjcmsidComesFromTeacherTablePageNotFromSlotCode() {
        when(jsxsd.openAuthPage()).thenReturn(authPage());
        when(jsxsd.checkCaptcha(any(), anyString()))
                .thenReturn(new JsxsdClient.CaptchaChallenge(false, null, null, null));
        String attemptId = service.startAttempt(1L, new CaptchaRequest(USERNAME)).attemptId();

        when(jsxsd.login(any(), anyString(), anyString(), any())).thenReturn(jwCookies());
        when(jsxsd.fetchTeacherTablePage(any())).thenReturn("<select id=\"kbjcmsid\">"
                + "<option value=\"1\">时间模式一</option>"
                + "<option selected value=\"3\">节次模式</option></select>");
        when(jsxsd.extractKbjcmsid(anyString())).thenCallRealMethod();
        when(jsxsd.fetchTeacherTable(any(), anyString(), anyString()))
                .thenReturn(html(teacherRow("张三", 1)));
        linkTeacher("张三", 7L);

        service.sync(1L, new SyncRequest(attemptId, "2026-2027-1", PASSWORD, null, null, null, null));

        ArgumentCaptor<String> kbjcmsid = ArgumentCaptor.forClass(String.class);
        verify(jsxsd).fetchTeacherTable(any(), anyString(), kbjcmsid.capture());
        assertThat(kbjcmsid.getValue()).isEqualTo("3");
        // 节次组 0102 绝不能当成 kbjcmsid。
        assertThat(kbjcmsid.getValue()).isNotEqualTo("0102");
    }

    @Test
    void successfulSyncReplacesPreviousBatchForSameSemester() {
        when(jsxsd.openAuthPage()).thenReturn(authPage());
        when(jsxsd.checkCaptcha(any(), anyString()))
                .thenReturn(new JsxsdClient.CaptchaChallenge(false, null, null, null));
        String attemptId = service.startAttempt(1L, new CaptchaRequest(USERNAME)).attemptId();

        when(jsxsd.login(any(), anyString(), anyString(), any())).thenReturn(jwCookies());
        when(jsxsd.fetchTeacherTable(any(), anyString(), anyString()))
                .thenReturn(html(teacherRow("张三", 1)));
        linkTeacher("张三", 7L);
        TimetableImportBatch previous = new TimetableImportBatch();
        previous.setId(99L);
        previous.setSemesterCode("2026-2027-1");
        when(batches.selectList(any())).thenReturn(List.of(previous));

        SyncResponse response = service.sync(1L,
                new SyncRequest(attemptId, "2026-2027-1", PASSWORD, null, null, null, null));

        assertThat(response.semesterCode()).isEqualTo("2026-2027-1");
        assertThat(response.teacherCount()).isEqualTo(1);
        assertThat(response.cellCount()).isEqualTo(1);
        // 旧批次连同旧单元格一起删除，再写入新批次。
        verify(cells).delete(any());
        verify(batches).deleteById(99L);
        verify(batches).insert(any(TimetableImportBatch.class));
        verify(cells).insert(any(TimetableCell.class));
    }

    @Test
    void attemptIsConsumedSoItCannotBeReplayed() {
        when(jsxsd.openAuthPage()).thenReturn(authPage());
        when(jsxsd.checkCaptcha(any(), anyString()))
                .thenReturn(new JsxsdClient.CaptchaChallenge(false, null, null, null));
        String attemptId = service.startAttempt(1L, new CaptchaRequest(USERNAME)).attemptId();

        when(jsxsd.login(any(), anyString(), anyString(), any())).thenReturn(jwCookies());
        when(jsxsd.fetchTeacherTable(any(), anyString(), anyString()))
                .thenThrow(new BusinessException(org.springframework.http.HttpStatus.BAD_GATEWAY, "expired"));

        assertThatThrownBy(() -> service.sync(1L,
                new SyncRequest(attemptId, "2026-2027-1", PASSWORD, null, null, null, null)))
                .isInstanceOf(BusinessException.class);

        // 失败后尝试已被清除：同一个 attemptId 不能再次使用。
        assertThatThrownBy(() -> service.sync(1L,
                new SyncRequest(attemptId, "2026-2027-1", PASSWORD, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void passwordIsEncryptedBeforeBeingSentAndNeverPersisted() {
        when(jsxsd.openAuthPage()).thenReturn(authPage());
        when(jsxsd.checkCaptcha(any(), anyString()))
                .thenReturn(new JsxsdClient.CaptchaChallenge(false, null, null, null));
        String attemptId = service.startAttempt(1L, new CaptchaRequest(USERNAME)).attemptId();

        when(jsxsd.login(any(), anyString(), anyString(), any())).thenReturn(jwCookies());
        when(jsxsd.fetchTeacherTable(any(), anyString(), anyString()))
                .thenReturn(html(teacherRow("张三", 1)));
        linkTeacher("张三", 7L);

        service.sync(1L, new SyncRequest(attemptId, "2026-2027-1", PASSWORD, null, null, null, null));

        ArgumentCaptor<String> encrypted = ArgumentCaptor.forClass(String.class);
        verify(jsxsd).login(any(), anyString(), encrypted.capture(), any());
        // 传给教务的是加密串，明文口令不出现在调用参数里。
        assertThat(encrypted.getValue()).isNotEqualTo(PASSWORD).doesNotContain(PASSWORD);

        ArgumentCaptor<TimetableCell> cell = ArgumentCaptor.forClass(TimetableCell.class);
        verify(cells).insert(cell.capture());
        assertThat(cell.getValue().getOccupancyText()).doesNotContain(PASSWORD);
    }

    @Test
    void blankUsernameIsRejected() {
        assertThatThrownBy(() -> service.startAttempt(1L, new CaptchaRequest("   ")))
                .isInstanceOf(BusinessException.class);
        verify(jsxsd, never()).openAuthPage();
    }

    @Test
    void mappedTeacherWithNoClassesGetsImportRecordButNoCells() {
        when(jsxsd.openAuthPage()).thenReturn(authPage());
        when(jsxsd.checkCaptcha(any(), anyString()))
                .thenReturn(new JsxsdClient.CaptchaChallenge(false, null, null, null));
        String attemptId = service.startAttempt(1L, new CaptchaRequest(USERNAME)).attemptId();

        when(jsxsd.login(any(), anyString(), anyString(), any())).thenReturn(jwCookies());
        // 张三整学期无课（42 列全空），但出现在总表中。
        when(jsxsd.fetchTeacherTable(any(), anyString(), anyString()))
                .thenReturn(html(teacherRow("张三")));
        linkTeacher("张三", 7L);

        SyncResponse response = service.sync(1L,
                new SyncRequest(attemptId, "2026-2027-1", PASSWORD, null, null, null, null));

        // 有导入记录：教师参与空闲判断，不会被当成 ABSENT_FROM_IMPORT。
        ArgumentCaptor<TimetableImportTeacher> marker =
                ArgumentCaptor.forClass(TimetableImportTeacher.class);
        verify(importTeachers).insert(marker.capture());
        assertThat(marker.getValue().getTeacherId()).isEqualTo(7L);
        // 没有单元格，也没有单元格插入。
        verify(cells, never()).insert(any(TimetableCell.class));
        assertThat(response.cellCount()).isZero();
        assertThat(response.teacherCount()).isEqualTo(1);
    }

    @Test
    void replacingBatchAlsoDeletesPreviousImportTeachers() {
        when(jsxsd.openAuthPage()).thenReturn(authPage());
        when(jsxsd.checkCaptcha(any(), anyString()))
                .thenReturn(new JsxsdClient.CaptchaChallenge(false, null, null, null));
        String attemptId = service.startAttempt(1L, new CaptchaRequest(USERNAME)).attemptId();

        when(jsxsd.login(any(), anyString(), anyString(), any())).thenReturn(jwCookies());
        when(jsxsd.fetchTeacherTable(any(), anyString(), anyString()))
                .thenReturn(html(teacherRow("张三", 1)));
        linkTeacher("张三", 7L);
        TimetableImportBatch previous = new TimetableImportBatch();
        previous.setId(99L);
        previous.setSemesterCode("2026-2027-1");
        when(batches.selectList(any())).thenReturn(List.of(previous));

        service.sync(1L, new SyncRequest(attemptId, "2026-2027-1", PASSWORD, null, null, null, null));

        // 旧批次的导入教师记录跟旧单元格一起删除。
        verify(importTeachers).delete(any());
        verify(batches).deleteById(99L);
    }
}
