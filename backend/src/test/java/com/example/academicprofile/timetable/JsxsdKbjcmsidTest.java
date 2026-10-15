package com.example.academicprofile.timetable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.example.academicprofile.common.exception.BusinessException;

/** 校验 kbjcmsid 只从总表页 `#kbjcmsid` 已选值取得，且永不回退成节次组 0102。 */
class JsxsdKbjcmsidTest {

    private final JsxsdClient client = new JsxsdClient(
            new TimetableProperties(), new JsxsdPasswordCipher(new java.security.SecureRandom()), new com.fasterxml.jackson.databind.ObjectMapper());

    @Test
    void picksSelectedOptionValue() {
        String html = "<html><body><select id=\"kbjcmsid\" name=\"kbjcmsid\">"
                + "<option value=\"1\">时间模式一</option>"
                + "<option value=\"3\" selected>节次模式</option>"
                + "</select></body></html>";

        assertThat(client.extractKbjcmsid(html)).isEqualTo("3");
    }

    @Test
    void selectedAttributeBeforeValueIsAccepted() {
        String html = "<select id='kbjcmsid'><option selected='selected' value='7'>模式</option></select>";

        assertThat(client.extractKbjcmsid(html)).isEqualTo("7");
    }

    @Test
    void noSelectedOptionFailsInsteadOfTakingTheFirst() {
        // 没有 selected 时禁止取第一个选项，必须报错。
        String html = "<select id=\"kbjcmsid\">"
                + "<option value=\"5\">模式五</option><option value=\"6\">模式六</option></select>";

        assertThatThrownBy(() -> client.extractKbjcmsid(html))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("kbjcmsid");
    }

    @Test
    void singleOptionWithoutSelectedIsUsed() {
        // 只有一个带 value 的选项时，即使没有 selected 也不得报错。
        String html = "<select id=\"kbjcmsid\"><option value=\"3\">节次模式</option></select>";

        assertThat(client.extractKbjcmsid(html)).isEqualTo("3");
    }

    @Test
    void multipleOptionsWithoutSelectedAreRejected() {
        // 多个选项且都没有 selected：不许取第一个，也不许回退成 0102。
        String html = "<select id=\"kbjcmsid\" name=\"kbjcmsid\">"
                + "<option value=\"0102\">第一大节</option>"
                + "<option value=\"0304\">第二大节</option>"
                + "<option value=\"0506\">第三大节</option>"
                + "</select>";

        assertThatThrownBy(() -> client.extractKbjcmsid(html))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("kbjcmsid");
    }

    @Test
    void selectedOptionWinsOverAnEarlierOption() {
        // 有 selected 时仍以它为准，不受“唯一选项”规则影响。
        String html = "<select id=\"kbjcmsid\">"
                + "<option value=\"0102\">第一大节</option>"
                + "<option value=\"0910\" selected>第五大节</option>"
                + "<option value=\"1112\">第六大节</option>"
                + "</select>";

        assertThat(client.extractKbjcmsid(html)).isEqualTo("0910");
    }

    @Test
    void singleOptionWithoutValueIsStillRejected() {
        // 唯一选项没有 value 时不能当成可用值。
        String html = "<select id=\"kbjcmsid\"><option>节次模式</option></select>";

        assertThatThrownBy(() -> client.extractKbjcmsid(html))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("kbjcmsid");
    }

    @Test
    void selectedOptionWithoutValueIsRejected() {
        String html = "<select id=\"kbjcmsid\"><option selected>无值</option>";

        assertThatThrownBy(() -> client.extractKbjcmsid(html))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void neverFallsBackToSlotCode0102() {
        // 页面里只有节次组 0102、没有 #kbjcmsid 时，必须报错而不是拿 0102 当时间模式。
        String html = "<table id=\"kbtable\"><tr><td>0102</td></tr></table>";

        assertThatThrownBy(() -> client.extractKbjcmsid(html))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("kbjcmsid");
    }

    @Test
    void blankOrMissingPageFailsLoudly() {
        assertThatThrownBy(() -> client.extractKbjcmsid(null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> client.extractKbjcmsid(""))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> client.extractKbjcmsid("<html><body>无下拉框</body></html>"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("kbjcmsid");
    }

    @Test
    void teacherTablePageUrlPointsAtKbxxTeacher() {
        assertThat(new TimetableProperties().jwTeacherTablePageUrl())
                .endsWith("/jsxsd/kbcx/kbxx_teacher");
    }

    @Test
    void defaultJwBaseIsHttps() {
        // 教务走 https，且 sso、总表页、总表数据都挂在这个基址上。
        TimetableProperties properties = new TimetableProperties();
        assertThat(properties.getJwBaseUrl()).startsWith("https://jw.dgpt.edu.cn");
        assertThat(properties.jwSsoUrl()).startsWith("https://jw.dgpt.edu.cn");
        assertThat(properties.jwTeacherTablePageUrl()).startsWith("https://jw.dgpt.edu.cn");
        assertThat(properties.jwTeacherTableUrl()).startsWith("https://jw.dgpt.edu.cn");
    }

    @Test
    void pageWithoutKbjcmsidIsRejected() {
        // 跟随跳转后拿到登录页（没有 #kbjcmsid）时，必须报错，不能交给解析。
        String loginPage = "<html><body><form action=\"/authserver/login\">"
                + "<input name=\"username\"><input name=\"password\"></form></body></html>";

        assertThatThrownBy(() -> client.extractKbjcmsid(loginPage))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("kbjcmsid");
    }
}
