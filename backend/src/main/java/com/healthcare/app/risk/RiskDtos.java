package com.healthcare.app.risk;

import com.healthcare.app.risk.RiskAssessment.Level;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

public final class RiskDtos {

    private RiskDtos() {
    }

    /** The model's training data covers adults 21+, so younger ages are rejected rather than extrapolated. */
    public record RiskRequest(
            @NotNull(message = "Age is required")
            @Min(value = 21, message = "This check is for adults aged 21 and over")
            @Max(value = 100, message = "Enter an age of 100 or less") Integer age,
            @DecimalMin(value = "40", message = "Glucose must be between 40 and 400 mg/dL")
            @DecimalMax(value = "400", message = "Glucose must be between 40 and 400 mg/dL") Double glucose,
            @DecimalMin(value = "12", message = "BMI must be between 12 and 70")
            @DecimalMax(value = "70", message = "BMI must be between 12 and 70") Double bmi,
            @Min(value = 0, message = "Pregnancies must be between 0 and 20")
            @Max(value = 20, message = "Pregnancies must be between 0 and 20") Integer pregnancies) {
    }

    /** impact is in log-odds; direction is derived so the UI never has to interpret the sign. */
    public record Factor(String name, String label, String unit, Double value, double impact, String direction) {
    }

    /** severity: "warning" or "urgent". Produced by fixed rules, never by the model. */
    public record Flag(String severity, String message) {
    }

    public record ModelInfo(String version, String trainedOn, double cvAuc, double cvAucWithoutGlucose) {
    }

    public record RiskResponse(
            Long id,
            Instant createdAt,
            double score,
            Level level,
            List<Factor> factors,
            List<String> missing,
            List<String> outOfRange,
            List<Flag> flags,
            String reliabilityNote,
            ModelInfo model,
            String disclaimer) {
    }

    public record HistoryItem(Long id, Instant createdAt, double score, Level level,
                              int age, Double glucose, Double bmi, Integer pregnancies) {
        public static HistoryItem from(RiskAssessment a) {
            return new HistoryItem(a.getId(), a.getCreatedAt(), a.getScore(), a.getLevel(),
                    a.getAge(), a.getGlucose(), a.getBmi(), a.getPregnancies());
        }
    }
}
