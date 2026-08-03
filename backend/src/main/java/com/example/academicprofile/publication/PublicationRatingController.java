package com.example.academicprofile.publication;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.academicprofile.common.ApiResponse;

@RestController
@RequestMapping("/api")
public class PublicationRatingController {
    private final PublicationRatingQueryService ratings;

    public PublicationRatingController(PublicationRatingQueryService ratings) {
        this.ratings = ratings;
    }

    @GetMapping("/publication-venues/search")
    public ApiResponse<PublicationRatingQueryService.VenueSearchResponse> search(
            @RequestParam String query,
            @RequestParam VenueType type,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(ratings.search(query, type, limit));
    }

    @GetMapping("/journal-metrics/suggestions")
    public ApiResponse<List<PublicationRatingQueryService.JournalMetricSuggestion>> suggestions(
            @RequestParam(required = false) String issn,
            @RequestParam(required = false) String eissn,
            @RequestParam(required = false) String journalName,
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(ratings.metricSuggestions(issn, eissn, journalName, year, limit));
    }
}
