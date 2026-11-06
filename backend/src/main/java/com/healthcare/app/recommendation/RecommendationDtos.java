package com.healthcare.app.recommendation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class RecommendationDtos {

    private RecommendationDtos() {
    }

    public record RecommendRequest(
            @NotBlank(message = "Describe your symptoms")
            @Size(min = 5, max = 1000, message = "Use between 5 and 1000 characters") String symptoms) {
    }

    public record SpecialtyScore(String specialty, double confidence) {
    }

    /** matchScore = the model's confidence in this doctor's specialty (0..1). */
    public record DoctorMatch(Long id, String fullName, String specialty, double matchScore,
                              String reason, int upcomingBookings) {
    }

    public record RecommendResponse(
            boolean emergency,
            String emergencyMessage,
            boolean lowConfidence,
            String note,
            List<SpecialtyScore> specialties,
            List<DoctorMatch> doctors,
            String disclaimer) {
    }
}
