package com.example.academicprofile.ocr;

import java.util.List;
import com.example.academicprofile.achievement.CompetitionCategory;

public record OcrConfirmRequest(List<Long> resultIds, CompetitionCategory competitionCategory) {
}
