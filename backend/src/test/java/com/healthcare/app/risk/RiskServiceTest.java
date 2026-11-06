package com.healthcare.app.risk;

import com.healthcare.app.risk.RiskAssessment.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Risk level bands")
class RiskServiceTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"0,LOW", "29.99,LOW", "30,MODERATE", "59.99,MODERATE", "60,HIGH", "100,HIGH"})
    void bandsHaveExactBoundaries(double score, Level expected) {
        assertEquals(expected, RiskService.level(score));
    }
}
