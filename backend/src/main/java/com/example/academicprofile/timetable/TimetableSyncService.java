package com.example.academicprofile.timetable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.academicprofile.admin.AdminAuditService;
import com.example.academicprofile.common.exception.BusinessException;
import com.example.academicprofile.timetable.KbtableParser.ParsedCell;
import com.example.academicprofile.timetable.KbtableParser.ParsedTeacherRow;
import com.example.academicprofile.timetable.TimetableDtos.CaptchaRequest;
import com.example.academicprofile.timetable.TimetableDtos.CaptchaResponse;
import com.example.academicprofile.timetable.TimetableDtos.SyncRequest;
import com.example.academicprofile.timetable.TimetableDtos.SyncResponse;
import com.example.academicprofile.timetable.TimetableSyncAttemptStore.JsxsdAuthPage;

@Service
public class TimetableSyncService {

    private static final int COURSE_NAME_MAX = 255;
    private static final int OCCUPANCY_TEXT_MAX = 1000;

    private final JsxsdClient jsxsd;
    private final JsxsdPasswordCipher passwordCipher;
    private final KbtableParser parser;
    private final TimetableSyncAttemptStore attempts;
    private final TimetableTeacherLinkMapper links;
    private final TimetableImportBatchMapper batches;
    private final TimetableCellMapper cells;
    private final TimetableImportTeacherMapper importTeachers;
    private final AdminAuditService audit;

    public TimetableSyncService(
            JsxsdClient jsxsd,
            JsxsdPasswordCipher passwordCipher,
            KbtableParser parser,
            TimetableSyncAttemptStore attempts,
            TimetableTeacherLinkMapper links,
            TimetableImportBatchMapper batches,
            TimetableCellMapper cells,
            TimetableImportTeacherMapper importTeachers,
            AdminAuditService audit) {
        this.jsxsd = jsxsd;
        this.passwordCipher = passwordCipher;
        this.parser = parser;
        this.attempts = attempts;
        this.links = links;
        this.batches = batches;
        this.cells = cells;
        this.importTeachers = importTeachers;
        this.audit = audit;
    }

    /** 第一步：打开认证页并判断是否需要验证码。临时状态只留在内存。 */
    public CaptchaResponse startAttempt(Long operatorUserId, CaptchaRequest request) {
        String username = request.username().trim();
        if (username.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "username must not be blank");
        }
        JsxsdAuthPage authPage = jsxsd.openAuthPage();
        String attemptId = attempts.create(username, authPage);
        JsxsdClient.CaptchaChallenge challenge = jsxsd.checkCaptcha(authPage, username);
        if (challenge.required() && challenge.safeSecure() != null) {
            // safeSecure 只留本次尝试的内存，用完即删。
            attempts.putSafeSecure(attemptId, challenge.safeSecure());
        }
        audit.record(operatorUserId, "TIMETABLE_SYNC_ATTEMPT", null, true,
                "Timetable sync attempt started; captchaRequired=" + challenge.required());
        return new CaptchaResponse(
                attemptId,
                challenge.required(),
                null,
                challenge.required(),
                challenge.backgroundBase64(),
                challenge.pieceBase64());
    }

    /**
     * 第二步：用同一次尝试登录教务并同步一个学期。
     * 未映射教师跳过并记入响应的 unmappedNames，不让整批失败；
     * 已映射教师的非空单元格照常保存。同一 semesterCode 再次同步替换旧批次。
     */
    @Transactional
    public SyncResponse sync(Long operatorUserId, SyncRequest request) {
        String semesterCode = request.semesterCode().trim();
        TimetableSyncAttemptStore.Attempt attempt = attempts.require(request.attemptId());
        try {
            // 需要滑块时必须先校验通过；不通过就不能发登录请求。
            if (attempt.safeSecure() != null) {
                boolean verified = jsxsd.verifySliderCaptcha(
                        attempt.authPage(),
                        attempt.safeSecure(),
                        request.sliderCanvasWidth(),
                        request.sliderMove(),
                        request.sliderTracks());
                if (!verified) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST,
                            "Slider captcha verification failed");
                }
            }
            String encryptedPassword =
                    passwordCipher.encrypt(request.password(), attempt.authPage().pwdEncryptSalt()).ciphertext();
            TimetableSyncAttemptStore.CookieJar jwCookies = jsxsd.login(
                    attempt.authPage(), attempt.username(), encryptedPassword, request.captcha());
            // kbjcmsid 取总表页 #kbjcmsid 的已选值，不能用节次组 0102 代替。
            String kbjcmsid = jsxsd.extractKbjcmsid(jsxsd.fetchTeacherTablePage(jwCookies));
            String html = jsxsd.fetchTeacherTable(jwCookies, semesterCode, kbjcmsid);
            List<ParsedTeacherRow> rows = parser.parse(html);
            if (rows.isEmpty()) {
                throw new BusinessException(HttpStatus.BAD_GATEWAY,
                        "Academic affairs returned no teacher rows for " + semesterCode);
            }
            Map<String, Long> mapping = loadMapping();
            List<String> unmapped = unmappedNames(rows, mapping);
            // 全校约 760 名教师，不可能先映射全部：未映射教师跳过，其余照常保存。
            return persist(operatorUserId, semesterCode, rows, mapping, unmapped);
        } finally {
            // 无论成功失败都不留下登录态。
            attempts.remove(request.attemptId());
        }
    }

    private Map<String, Long> loadMapping() {
        Map<String, Long> mapping = new LinkedHashMap<>();
        for (TimetableTeacherLink link : links.selectList(null)) {
            mapping.put(link.getSourceName(), link.getTeacherId());
        }
        return mapping;
    }

    private SyncResponse persist(
            Long operatorUserId,
            String semesterCode,
            List<ParsedTeacherRow> rows,
            Map<String, Long> mapping,
            List<String> unmappedNames) {
        List<TimetableImportBatch> existing = batches.selectList(
                new LambdaQueryWrapper<TimetableImportBatch>()
                        .eq(TimetableImportBatch::getSemesterCode, semesterCode));
        for (TimetableImportBatch old : existing) {
            cells.delete(new LambdaQueryWrapper<TimetableCell>()
                    .eq(TimetableCell::getBatchId, old.getId()));
            // 旧的导入教师记录一并删除，避免残留上一批的教师。
            importTeachers.delete(new LambdaQueryWrapper<TimetableImportTeacher>()
                    .eq(TimetableImportTeacher::getBatchId, old.getId()));
            batches.deleteById(old.getId());
        }

        TimetableImportBatch batch = new TimetableImportBatch();
        batch.setSemesterCode(semesterCode);
        batch.setCreatedBy(operatorUserId);
        batches.insert(batch);

        Set<Long> teacherIds = new LinkedHashSet<>();
        int cellCount = 0;
        for (ParsedTeacherRow row : rows) {
            Long teacherId = mapping.get(row.teacherName());
            if (teacherId == null) {
                continue;
            }
            teacherIds.add(teacherId);
            // 已映射且出现在总表中的教师都要留痕：整学期无课也要写，
            // 供查询区分 ABSENT_FROM_IMPORT。空单元格仍不入 timetable_cell。
            TimetableImportTeacher marker = new TimetableImportTeacher();
            marker.setBatchId(batch.getId());
            marker.setTeacherId(teacherId);
            importTeachers.insert(marker);
            for (ParsedCell parsed : row.cells()) {
                TimetableCell cell = new TimetableCell();
                cell.setBatchId(batch.getId());
                cell.setTeacherId(teacherId);
                cell.setDayOfWeek(parsed.dayOfWeek());
                cell.setSlotCode(parsed.slotCode());
                cell.setWeekStart(parsed.weekStart());
                cell.setWeekEnd(parsed.weekEnd());
                cell.setOccupancyText(truncate(parsed.occupancyText(), OCCUPANCY_TEXT_MAX));
                cell.setCourseName(truncate(firstLine(parsed.occupancyText()), COURSE_NAME_MAX));
                cells.insert(cell);
                cellCount++;
            }
        }
        audit.record(operatorUserId, "TIMETABLE_SYNC_IMPORT", null, true,
                "Timetable batch imported for " + semesterCode
                        + "; teachers=" + teacherIds.size() + ", cells=" + cellCount
                        + ", unmapped=" + unmappedNames.size());
        return new SyncResponse(
                batch.getId(), semesterCode, teacherIds.size(), cellCount, unmappedNames);
    }

    private static String firstLine(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        int newline = text.indexOf('\n');
        String line = newline < 0 ? text : text.substring(0, newline);
        return line.isBlank() ? "" : line.trim();
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    /** 保持出现顺序并去重，便于运维按名单处理映射。 */
    private static List<String> unmappedNames(
            List<ParsedTeacherRow> rows, Map<String, Long> mapping) {
        List<String> unmapped = new ArrayList<>();
        for (ParsedTeacherRow row : rows) {
            if (!mapping.containsKey(row.teacherName()) && !unmapped.contains(row.teacherName())) {
                unmapped.add(row.teacherName());
            }
        }
        return unmapped;
    }
}
