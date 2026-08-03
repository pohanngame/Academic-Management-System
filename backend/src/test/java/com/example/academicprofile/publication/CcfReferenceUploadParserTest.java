package com.example.academicprofile.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class CcfReferenceUploadParserTest {
    private final CcfReferenceUploadParser parser = new CcfReferenceUploadParser(new ObjectMapper());

    @Test
    void parsesStrictSyntheticDatasetWithoutFollowingUrls() {
        CcfReferenceUploadParser.ParsedDataset parsed = parser.parse(input(validJson()), 2026);
        assertThat(parsed.categoryCount()).isEqualTo(1);
        assertThat(parsed.journalCount()).isEqualTo(1);
        assertThat(parsed.conferenceCount()).isEqualTo(1);
        assertThat(parsed.references()).extracting(CcfReferenceUploadParser.ParsedReference::standardName)
                .containsExactly("Synthetic Journal", "Synthetic Conference");
    }

    @Test
    void rejectsInvalidRankAndMissingCategory() {
        assertThatThrownBy(() -> parser.parse(input(validJson().replace("\"rank\": \"A\"", "\"rank\": \"D\"")), 2026))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parse(input(validJson().replace("\"category_id\": 1", "\"category_id\": 9")), 2026))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsWrongYearAndMalformedJson() {
        assertThatThrownBy(() -> parser.parse(input(validJson().replace("\"year\": 2026", "\"year\": 2025")), 2026))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parse(input("not-json"), 2026))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsConflictingNormalizedNamesAndOversizedFields() {
        String duplicate = validJson().replace(
                "\"journals\": [",
                "\"journals\": [{\"category_id\":1,\"rank\":\"B\",\"abbr\":\"SJ2\",\"name\":\"Synthetic  Journal\",\"publisher\":\"P\",\"url\":\"\"},");
        assertThatThrownBy(() -> parser.parse(input(duplicate), 2026))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parse(input(validJson().replace("Synthetic Journal", "X".repeat(501))), 2026))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allowsSameVenueInDifferentCategoriesButRejectsDuplicateClassification() {
        String crossCategory = validJson()
                .replace(
                        "{ \"id\": 1, \"chinese\": \"合成领域\", \"english\": \"Synthetic Category\" }",
                        "{ \"id\": 1, \"chinese\": \"合成领域\", \"english\": \"Synthetic Category\" },"
                                + "{ \"id\": 2, \"chinese\": \"第二领域\", \"english\": \"Second Category\" }")
                .replace(
                        "\"journals\": [",
                        "\"journals\": [{\"category_id\":2,\"rank\":\"A\",\"abbr\":\"SJ\","
                                + "\"name\":\"Synthetic Journal\",\"publisher\":\"P\",\"url\":\"\"},");

        CcfReferenceUploadParser.ParsedDataset parsed = parser.parse(input(crossCategory), 2026);
        assertThat(parsed.references())
                .filteredOn(reference -> reference.standardName().equals("Synthetic Journal"))
                .extracting(CcfReferenceUploadParser.ParsedReference::categoryId)
                .containsExactlyInAnyOrder(1, 2);

        String duplicateClassification = validJson().replace(
                "\"journals\": [",
                "\"journals\": [{\"category_id\":1,\"rank\":\"A\",\"abbr\":\"SJ2\","
                        + "\"name\":\"Synthetic  Journal\",\"publisher\":\"P\",\"url\":\"\"},");
        assertThatThrownBy(() -> parser.parse(input(duplicateClassification), 2026))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CCF 条目标准名称重复或冲突");
    }

    private ByteArrayInputStream input(String json) {
        return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    }

    static String validJson() {
        try (var input = CcfReferenceUploadParserTest.class.getResourceAsStream("/fixtures/ccf-synthetic.json")) {
            if (input == null) throw new IllegalStateException("Synthetic CCF fixture is missing");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Synthetic CCF fixture cannot be read", ex);
        }
    }
}
