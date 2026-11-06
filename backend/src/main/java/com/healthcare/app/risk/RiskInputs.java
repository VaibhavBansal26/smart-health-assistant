package com.healthcare.app.risk;

/** The measurements a person entered for a risk check. Stored encrypted as one JSON value. */
public record RiskInputs(int age, Double glucose, Double bmi, Integer pregnancies) {
}
