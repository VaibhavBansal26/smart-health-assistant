package com.healthcare.app.recommendation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.healthcare.app.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/** Calls the Python classifier. The symptom text is never logged (it is health data). */
@Component
public class MlClient {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(String specialty, double confidence) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Prediction(List<Item> predictions, @JsonProperty("low_confidence") boolean lowConfidence) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Contribution(String name, String label, String unit, Double value, double impact) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RiskResult(
            double score,
            List<Contribution> contributions,
            List<String> missing,
            @JsonProperty("out_of_range") List<String> outOfRange,
            @JsonProperty("model_version") String modelVersion,
            @JsonProperty("trained_on") String trainedOn,
            @JsonProperty("cv_auc") double cvAuc,
            @JsonProperty("cv_auc_without_glucose") double cvAucWithoutGlucose) {
    }

    private final RestClient client;

    public MlClient(@Value("${app.ml.url}") String url, @Value("${app.ml.timeout-ms}") int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(timeoutMs);
        this.client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    public Prediction predict(String text) {
        try {
            Prediction p = client.post().uri("/predict").body(Map.of("text", text)).retrieve().body(Prediction.class);
            if (p == null || p.predictions() == null || p.predictions().isEmpty()) {
                throw unavailable();
            }
            return p;
        } catch (RestClientException e) {
            throw unavailable();
        }
    }

    /** Body may contain nulls (unknown inputs); the Python side treats null as "not provided". */
    public RiskResult predictDiabetesRisk(Map<String, Object> body) {
        try {
            RiskResult r = client.post().uri("/risk/diabetes").body(body).retrieve().body(RiskResult.class);
            if (r == null || r.contributions() == null) {
                throw unavailable();
            }
            return r;
        } catch (RestClientException e) {
            throw unavailable();
        }
    }

    private ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "The analysis service is unavailable right now. Please try again shortly.");
    }
}
