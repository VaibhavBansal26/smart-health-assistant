package com.healthcare.app.recommendation;

import com.healthcare.app.exception.ApiException;
import com.healthcare.app.support.ApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Doctor recommender API")
class RecommendationApiTest extends ApiTest {

    private void mlSays(boolean lowConfidence, Object... specialtyAndConfidence) {
        var items = new java.util.ArrayList<MlClient.Item>();
        for (int i = 0; i < specialtyAndConfidence.length; i += 2) {
            items.add(new MlClient.Item((String) specialtyAndConfidence[i], (double) specialtyAndConfidence[i + 1]));
        }
        when(ml.predict(any())).thenReturn(new MlClient.Prediction(items, lowConfidence));
    }

    private org.springframework.test.web.servlet.ResultActions recommend(String token, String symptoms) throws Exception {
        return post("/api/recommendations", token, Map.of("symptoms", symptoms));
    }

    @Test
    void emergencyWording_isCaughtByRules_andTheModelIsNeverCalled() throws Exception {
        recommend(newPatient().token(), "I have chest pain and shortness of breath")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emergency").value(true))
                .andExpect(jsonPath("$.emergencyMessage", containsString("emergency")))
                .andExpect(jsonPath("$.doctors", hasSize(0)));
        verifyNoInteractions(ml);
    }

    @Test
    void confidentPrediction_ranksMatchingDoctors_withReasons() throws Exception {
        mlSays(false, "Cardiology", 0.9, "General Medicine", 0.07);
        recommend(newPatient().token(), "my heart races when I climb stairs")
                .andExpect(jsonPath("$.emergency").value(false))
                .andExpect(jsonPath("$.specialties[0].specialty").value("Cardiology"))
                .andExpect(jsonPath("$.doctors", hasSize(2)))
                .andExpect(jsonPath("$.doctors[*].specialty", everyItem(is("Cardiology"))))
                .andExpect(jsonPath("$.doctors[0].reason", containsString("90% match")))
                .andExpect(jsonPath("$.disclaimer", containsString("not a diagnosis")));
    }

    @Test
    void weakGuesses_belowTheThreshold_arenotRecommended() throws Exception {
        mlSays(false, "Dermatology", 0.95, "Cardiology", 0.04);
        recommend(newPatient().token(), "itchy rash on my arms")
                .andExpect(jsonPath("$.doctors[*].specialty", everyItem(is("Dermatology"))))
                .andExpect(jsonPath("$.specialties", hasSize(1))); // the 4% guess is hidden too
    }

    @Test
    void whenTheModelIsUnsure_generalPhysiciansComeFirst() throws Exception {
        mlSays(true, "Orthopedics", 0.2, "Psychiatry", 0.15);
        recommend(newPatient().token(), "I feel strange lately")
                .andExpect(jsonPath("$.lowConfidence").value(true))
                .andExpect(jsonPath("$.doctors[0].specialty").value("General Medicine"))
                .andExpect(jsonPath("$.doctors[0].reason", containsString("broad")));
    }

    @Test
    void ifNoSpecialistExists_fallsBackToGeneralPhysicians_andSaysSo() throws Exception {
        mlSays(false, "Podiatry", 0.9);
        recommend(newPatient().token(), "my toenail hurts")
                .andExpect(jsonPath("$.note", containsString("Podiatry")))
                .andExpect(jsonPath("$.doctors[*].specialty", everyItem(is("General Medicine"))));
    }

    @Test
    void whenTheModelServiceIsDown_returns503() throws Exception {
        when(ml.predict(any())).thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "down"));
        recommend(newPatient().token(), "headache for three days").andExpect(status().isServiceUnavailable());
    }

    @Test
    void validation_andAccessRules() throws Exception {
        recommend(newPatient().token(), "hi").andExpect(status().isBadRequest());
        recommend(seededDoctor("asha.verma@clinic.test").token(), "headache for three days").andExpect(status().isForbidden());
        recommend(null, "headache for three days").andExpect(status().isUnauthorized());
    }
}
