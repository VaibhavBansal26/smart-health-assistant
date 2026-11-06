"""Tests for the FastAPI ML service. Run with:  .venv/bin/python -m pytest

They use the real trained models (run train.py and train_risk.py first), so they also
act as a quality gate: a retrain that makes the models worse or the explanations
nonsensical will fail here.
"""
import json
import math
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app import app

ROOT = Path(__file__).resolve().parent.parent
client = TestClient(app)


def risk(**body):
    return client.post("/risk/diabetes", json=body)


# ---------------------------------------------------------------- health
def test_health_reports_the_model_is_loaded():
    r = client.get("/health")
    assert r.status_code == 200 and r.json()["model_loaded"] is True


# ---------------------------------------------------------------- specialty classifier
@pytest.mark.parametrize("text, expected", [
    ("my heart races when I climb stairs and I feel out of breath", "Cardiology"),
    ("itchy red rash on my arms and dry flaky skin", "Dermatology"),
    ("burning pain in my stomach after meals", "Gastroenterology"),
    ("dry cough for two weeks and I get wheezy at night", "Pulmonology"),
    ("my knee hurts when I climb stairs and it is swollen", "Orthopedics"),
    ("I feel hopeless and I do not enjoy anything anymore", "Psychiatry"),
])
def test_predict_gets_clear_cases_right(text, expected):
    r = client.post("/predict", json={"text": text})
    assert r.status_code == 200
    body = r.json()
    assert body["predictions"][0]["specialty"] == expected
    assert body["low_confidence"] is False


def test_predict_returns_top3_sorted_probabilities():
    body = client.post("/predict", json={"text": "headache and dizziness"}).json()
    confidences = [p["confidence"] for p in body["predictions"]]
    assert len(confidences) == 3
    assert confidences == sorted(confidences, reverse=True)
    assert all(0 <= c <= 1 for c in confidences) and sum(confidences) <= 1.0001


def test_predict_flags_vague_text_as_low_confidence():
    body = client.post("/predict", json={"text": "I feel strange lately"}).json()
    assert body["low_confidence"] is True


@pytest.mark.parametrize("text", ["", "ab", "x" * 1001])
def test_predict_rejects_bad_input(text):
    assert client.post("/predict", json={"text": text}).status_code == 422


def test_specialty_model_beats_a_quality_floor_on_unseen_sentences():
    from dataset import DEV_SET, TEST_SET
    import joblib
    model = joblib.load(ROOT / "model.joblib")
    data = DEV_SET + TEST_SET
    hits = sum(model.predict([t])[0] == s for t, s in data)
    assert hits / len(data) >= 0.85, f"top-1 accuracy fell to {hits}/{len(data)}"


# ---------------------------------------------------------------- diabetes risk
def test_risk_returns_score_and_all_four_factors():
    body = risk(age=52, glucose=165, bmi=36.4, pregnancies=4).json()
    assert 0 <= body["score"] <= 100
    assert {c["name"] for c in body["contributions"]} == {"age", "glucose", "bmi", "pregnancies"}
    assert body["missing"] == [] and body["out_of_range"] == []


def test_contributions_add_up_to_the_models_own_score():
    """The explanation must be exact, not decorative: intercept + sum(impacts) reproduces the score."""
    meta = json.loads((ROOT / "risk_meta.json").read_text())
    for body in [dict(age=52, glucose=165, bmi=36.4, pregnancies=4), dict(age=40), dict(age=33, bmi=29.0),
                 dict(age=70, glucose=88, bmi=22.0, pregnancies=0)]:
        r = risk(**body).json()
        logit = meta["intercept"] + sum(c["impact"] for c in r["contributions"])
        assert r["score"] == pytest.approx(100 / (1 + math.exp(-logit)), abs=0.1), body


def test_unknown_inputs_are_reported_and_contribute_nothing():
    r = risk(age=40, bmi=30).json()
    assert sorted(r["missing"]) == ["glucose", "pregnancies"]
    impacts = {c["name"]: c["impact"] for c in r["contributions"]}
    assert impacts["glucose"] == 0 and impacts["pregnancies"] == 0


def test_values_outside_the_training_range_are_flagged():
    assert risk(age=60, glucose=350, bmi=30).json()["out_of_range"] == ["glucose"]


@pytest.mark.parametrize("field, low, high", [("glucose", 70, 190), ("bmi", 20, 45), ("age", 25, 70), ("pregnancies", 0, 10)])
def test_more_of_a_risk_factor_never_lowers_the_score(field, low, high):
    """Guards the 'blood pressure lowers your risk' kind of nonsense: every input must push the right way."""
    base = dict(age=40, glucose=120, bmi=30, pregnancies=2)
    lo = risk(**{**base, field: low}).json()["score"]
    hi = risk(**{**base, field: high}).json()["score"]
    assert hi > lo, f"{field}: {low} -> {lo}, {high} -> {hi}"


def test_every_coefficient_is_positive():
    meta = json.loads((ROOT / "risk_meta.json").read_text())
    for f in meta["features"]:
        assert f["coef"] > 0, f"{f['name']} has coefficient {f['coef']}: the explanation would say it lowers risk"


def test_risk_model_meets_its_quality_floor():
    meta = json.loads((ROOT / "risk_meta.json").read_text())
    assert meta["cv_auc"] >= 0.80, "cross-validated AUC dropped below 0.80"
    assert meta["cv_auc_without_glucose"] >= 0.70


@pytest.mark.parametrize("body", [
    dict(age=20), dict(age=101), dict(glucose=100), dict(age=40, glucose=10), dict(age=40, glucose=500),
    dict(age=40, bmi=5), dict(age=40, pregnancies=-1), dict(age="forty"),
])
def test_risk_rejects_invalid_input(body):
    assert risk(**body).status_code == 422


def test_age_only_still_works_at_this_layer():
    # the "need glucose or BMI" rule lives in Spring Boot; this service just answers
    assert risk(age=40).status_code == 200
