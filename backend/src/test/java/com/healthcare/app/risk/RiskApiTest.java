package com.healthcare.app.risk;

import com.healthcare.app.exception.ApiException;
import com.healthcare.app.recommendation.MlClient;
import com.healthcare.app.support.ApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Risk API")
class RiskApiTest extends ApiTest {

    /** What the Python model service would return. */
    private MlClient.RiskResult mlResult(double score, List<String> missing, List<String> outOfRange) {
        return new MlClient.RiskResult(score,
                List.of(new MlClient.Contribution("glucose", "2-hour glucose (OGTT)", "mg/dL", 150.0, 1.2),
                        new MlClient.Contribution("age", "Age", "years", 50.0, 0.2),
                        new MlClient.Contribution("bmi", "BMI", "kg/m2", null, 0.0),
                        new MlClient.Contribution("pregnancies", "Pregnancies", "count", null, -0.01)),
                missing, outOfRange, "diabetes-lr-v1", "test data", 0.833, 0.732);
    }

    private void mlReturns(double score) {
        when(ml.predictDiabetesRisk(any())).thenReturn(mlResult(score, List.of(), List.of()));
    }

    @Test
    void assess_returnsScoreLevelAndExplanation_andSavesIt() throws Exception {
        var patient = newPatient();
        mlReturns(78.7);

        post("/api/risk/diabetes", patient.token(), Map.of("age", 52, "glucose", 165, "bmi", 36.4))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(78.7))
                .andExpect(jsonPath("$.level").value("HIGH"))
                .andExpect(jsonPath("$.factors[0].direction").value("RAISES"))
                .andExpect(jsonPath("$.factors[?(@.name=='pregnancies')].direction").value("NEUTRAL"))
                .andExpect(jsonPath("$.disclaimer", containsString("not a diagnosis")))
                .andExpect(jsonPath("$.id").exists());

        get("/api/risk/history", patient.token()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @ParameterizedTest(name = "score {0} is {1}")
    @CsvSource({"0.0,LOW", "29.9,LOW", "30.0,MODERATE", "59.9,MODERATE", "60.0,HIGH", "100.0,HIGH"})
    void levelBands_haveExactBoundaries(double score, String level) throws Exception {
        mlReturns(score);
        post("/api/risk/diabetes", newPatient().token(), Map.of("age", 40, "glucose", 100))
                .andExpect(jsonPath("$.level").value(level));
    }

    @Test
    void aGlucoseInTheDiabetesRange_alwaysShowsAWarning_whateverTheScore() throws Exception {
        mlReturns(5.0); // even if the model were wrong
        post("/api/risk/diabetes", newPatient().token(), Map.of("age", 40, "glucose", 200))
                .andExpect(jsonPath("$.flags[0].severity").value("warning"))
                .andExpect(jsonPath("$.flags[0].message", containsString("200")));
        post("/api/risk/diabetes", newPatient().token(), Map.of("age", 40, "glucose", 199))
                .andExpect(jsonPath("$.flags", hasSize(0)));
    }

    @Test
    void missingGlucose_explainsTheEstimateIsLessReliable() throws Exception {
        when(ml.predictDiabetesRisk(any())).thenReturn(mlResult(35, List.of("glucose"), List.of()));
        post("/api/risk/diabetes", newPatient().token(), Map.of("age", 40, "bmi", 30))
                .andExpect(jsonPath("$.reliabilityNote", containsString("less reliable")))
                .andExpect(jsonPath("$.missing[0]").value("glucose"));
    }

    @Test
    void outOfRangeValues_addACautionNote() throws Exception {
        when(ml.predictDiabetesRisk(any())).thenReturn(mlResult(90, List.of(), List.of("glucose")));
        post("/api/risk/diabetes", newPatient().token(), Map.of("age", 40, "glucose", 300))
                .andExpect(jsonPath("$.reliabilityNote", containsString("outside the range")));
    }

    @Test
    void ageAlone_isRejected_becauseItWouldJustEchoTheDatasetAverage() throws Exception {
        post("/api/risk/diabetes", newPatient().token(), Map.of("age", 40))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("glucose or your BMI")));
    }

    @Test
    void invalidInputs_areRejectedBeforeReachingTheModel() throws Exception {
        String token = newPatient().token();
        post("/api/risk/diabetes", token, Map.of("age", 18, "glucose", 100))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.age").exists());
        post("/api/risk/diabetes", token, Map.of("age", 40, "glucose", 900))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.glucose").exists());
        post("/api/risk/diabetes", token, Map.of("age", 40, "bmi", 5))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.bmi").exists());
        post("/api/risk/diabetes", token, Map.of("glucose", 100))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.age").exists());
        org.mockito.Mockito.verifyNoInteractions(ml);
    }

    @Test
    void whenTheModelServiceIsDown_theUserGets503_andNothingIsSaved() throws Exception {
        var patient = newPatient();
        when(ml.predictDiabetesRisk(any())).thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "down"));

        post("/api/risk/diabetes", patient.token(), Map.of("age", 40, "glucose", 100))
                .andExpect(status().isServiceUnavailable());
        get("/api/risk/history", patient.token()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void history_isPaged_sortable_filterable_andNewestFirstByDefault() throws Exception {
        var patient = newPatient();
        for (double score : new double[]{10, 45, 80}) {
            mlReturns(score);
            post("/api/risk/diabetes", patient.token(), Map.of("age", 40, "glucose", 100)).andExpect(status().isOk());
        }

        get("/api/risk/history?size=2", patient.token())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.content[0].score").value(80.0)); // newest first
        get("/api/risk/history?sort=score,asc", patient.token()).andExpect(jsonPath("$.content[0].score").value(10.0));
        get("/api/risk/history?level=MODERATE", patient.token())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].score").value(45.0));
        get("/api/risk/history?level=BANANA", patient.token()).andExpect(status().isBadRequest());
        get("/api/risk/history?sort=user", patient.token()).andExpect(status().isBadRequest());
    }

    @Test
    void history_isPrivate_andDeletable() throws Exception {
        var owner = newPatient();
        var other = newPatient();
        mlReturns(50);
        long id = body(post("/api/risk/diabetes", owner.token(), Map.of("age", 40, "glucose", 100)).andReturn()).get("id").asLong();

        get("/api/risk/history", other.token()).andExpect(jsonPath("$.totalElements").value(0));
        delete("/api/risk/history/" + id, other.token()).andExpect(status().isNotFound());
        get("/api/risk/history", owner.token()).andExpect(jsonPath("$.totalElements").value(1));

        delete("/api/risk/history/" + id, owner.token()).andExpect(status().isNoContent());
        get("/api/risk/history", owner.token()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void deleteAll_removesOnlyMyRecords() throws Exception {
        var me = newPatient();
        var other = newPatient();
        mlReturns(50);
        post("/api/risk/diabetes", me.token(), Map.of("age", 40, "glucose", 100));
        post("/api/risk/diabetes", me.token(), Map.of("age", 41, "glucose", 100));
        post("/api/risk/diabetes", other.token(), Map.of("age", 42, "glucose", 100));

        delete("/api/risk/history", me.token()).andExpect(status().isNoContent());
        get("/api/risk/history", me.token()).andExpect(jsonPath("$.totalElements").value(0));
        get("/api/risk/history", other.token()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void doctorsAndAnonymousUsers_areBlocked() throws Exception {
        String doctor = seededDoctor("asha.verma@clinic.test").token();
        post("/api/risk/diabetes", doctor, Map.of("age", 40, "glucose", 100)).andExpect(status().isForbidden());
        get("/api/risk/history", doctor).andExpect(status().isForbidden());
        post("/api/risk/diabetes", null, Map.of("age", 40, "glucose", 100)).andExpect(status().isUnauthorized());
    }
}
