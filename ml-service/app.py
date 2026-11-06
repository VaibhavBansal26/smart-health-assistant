"""FastAPI wrapper around the trained classifier.

Run:  .venv/bin/uvicorn app:app --port 8000
Spring Boot is the only client; it is responsible for auth and safety rules.
"""
import os

import joblib
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

MODEL_PATH = os.environ.get("MODEL_PATH", "model.joblib")
LOW_CONFIDENCE = 0.35  # below this, the top guess is not trustworthy
TOP_K = 3

app = FastAPI(title="Specialty recommender", version="1.0")
_model = None


def get_model():
    global _model
    if _model is None:
        if not os.path.exists(MODEL_PATH):
            raise HTTPException(status_code=503, detail="Model not trained yet. Run train.py first.")
        _model = joblib.load(MODEL_PATH)
    return _model


class PredictRequest(BaseModel):
    text: str = Field(min_length=3, max_length=1000)


class Prediction(BaseModel):
    specialty: str
    confidence: float


class PredictResponse(BaseModel):
    predictions: list[Prediction]
    low_confidence: bool


@app.get("/health")
def health():
    return {"status": "ok", "model_loaded": os.path.exists(MODEL_PATH)}


@app.post("/predict", response_model=PredictResponse)
def predict(req: PredictRequest):
    model = get_model()
    proba = model.predict_proba([req.text])[0]
    classes = model.classes_
    order = proba.argsort()[::-1][:TOP_K]
    preds = [Prediction(specialty=str(classes[i]), confidence=round(float(proba[i]), 4)) for i in order]
    return PredictResponse(predictions=preds, low_confidence=preds[0].confidence < LOW_CONFIDENCE)


# ---------------------------------------------------------------- diabetes risk
import json  # noqa: E402
import math  # noqa: E402
from typing import Optional  # noqa: E402

import pandas as pd  # noqa: E402

RISK_MODEL_PATH = os.environ.get("RISK_MODEL_PATH", "risk_model.joblib")
RISK_META_PATH = os.environ.get("RISK_META_PATH", "risk_meta.json")
_risk = None


def get_risk():
    global _risk
    if _risk is None:
        if not (os.path.exists(RISK_MODEL_PATH) and os.path.exists(RISK_META_PATH)):
            raise HTTPException(status_code=503, detail="Risk model not trained yet. Run train_risk.py first.")
        with open(RISK_META_PATH) as f:
            _risk = (joblib.load(RISK_MODEL_PATH), json.load(f))
    return _risk


class RiskRequest(BaseModel):
    """Only age is required. Anything left out is treated as 'average', and reported as missing."""
    age: int = Field(ge=21, le=100)
    glucose: Optional[float] = Field(default=None, ge=40, le=400)
    bmi: Optional[float] = Field(default=None, ge=12, le=70)
    pregnancies: Optional[int] = Field(default=None, ge=0, le=20)


class Contribution(BaseModel):
    name: str
    label: str
    unit: str
    value: Optional[float]
    impact: float  # log-odds added to (+) or taken from (-) the average person's score


class RiskResponse(BaseModel):
    score: float  # 0-100
    contributions: list[Contribution]
    missing: list[str]
    out_of_range: list[str]
    model_version: str
    trained_on: str
    cv_auc: float
    cv_auc_without_glucose: float


@app.post("/risk/diabetes", response_model=RiskResponse)
def risk_diabetes(req: RiskRequest):
    model, meta = get_risk()
    values = {"age": req.age, "glucose": req.glucose, "bmi": req.bmi, "pregnancies": req.pregnancies}
    columns = [f["name"] for f in meta["features"]]
    frame = pd.DataFrame([{c: values.get(c) for c in columns}], dtype=float)  # None -> NaN -> mean-imputed

    score = float(model.predict_proba(frame)[0, 1])

    contributions, missing, out_of_range = [], [], []
    for f in meta["features"]:
        v = values.get(f["name"])
        if v is None:
            missing.append(f["name"])
            impact = 0.0  # imputed with the training mean, so it adds nothing
        else:
            impact = f["coef"] * (v - f["mean"]) / f["scale"]
            if not (f["train_min"] <= v <= f["train_max"]):
                out_of_range.append(f["name"])
        contributions.append(Contribution(name=f["name"], label=f["label"], unit=f["unit"], value=v,
                                          impact=round(impact, 4)))
    contributions.sort(key=lambda c: abs(c.impact), reverse=True)

    return RiskResponse(
        score=round(score * 100, 1), contributions=contributions, missing=missing, out_of_range=out_of_range,
        model_version=meta["version"], trained_on=meta["trained_on"], cv_auc=round(meta["cv_auc"], 3),
        cv_auc_without_glucose=round(meta["cv_auc_without_glucose"], 3))
