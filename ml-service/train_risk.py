"""Trains the diabetes risk model on the Pima dataset (data/pima.csv, see fetch_data.py).

Run:  .venv/bin/python train_risk.py
Writes risk_model.joblib and risk_meta.json.

Design choices (and why):
 * Only 4 inputs a person can realistically know: pregnancies, glucose, BMI, age.
   Skin-fold thickness and insulin are rarely known and are missing in 30-50% of rows;
   "pedigree function" is not something a user can enter. Diastolic blood pressure was tried
   and dropped: it added no predictive value (CV AUC 0.8325 with vs 0.8335 without) and its
   coefficient was slightly negative, which would make the explanation say "higher blood
   pressure lowers your risk", a dataset artifact and not medicine.
 * Zeros in glucose/blood pressure/BMI are impossible values = missing data in this dataset.
 * Missing values are filled with the training MEAN, so an unknown input contributes exactly
   zero to the score (and the API can say so).
 * Logistic regression: on ~770 rows it matches boosted trees, is well calibrated, and each
   input's contribution to the score is exact and explainable (coefficient x standardised value).
"""
import json

import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import HistGradientBoostingClassifier, RandomForestClassifier
from sklearn.impute import SimpleImputer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, brier_score_loss, roc_auc_score
from sklearn.model_selection import RepeatedStratifiedKFold, cross_validate
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler

DATA = "data/pima.csv"
VERSION = "diabetes-lr-v1"

FEATURES = ["pregnancies", "glucose", "bmi", "age"]
META = {
    "pregnancies": {"label": "Pregnancies", "unit": "count"},
    "glucose": {"label": "2-hour glucose (OGTT)", "unit": "mg/dL"},
    "bmi": {"label": "BMI", "unit": "kg/m2"},
    "age": {"label": "Age", "unit": "years"},
}
ALL8 = ["pregnancies", "glucose", "blood_pressure", "skin_thickness", "insulin", "bmi", "pedigree", "age"]


def load() -> pd.DataFrame:
    df = pd.read_csv(DATA)
    # a glucose / blood pressure / BMI / skin / insulin of 0 is not physiologically possible: missing
    for col in ["glucose", "blood_pressure", "bmi", "skin_thickness", "insulin"]:
        df[col] = df[col].replace(0, np.nan)
    return df


def lr() -> Pipeline:
    return Pipeline([("impute", SimpleImputer(strategy="mean")), ("scale", StandardScaler()),
                     ("clf", LogisticRegression(max_iter=1000, C=1.0))])


def cv_scores(model, x, y, cv):
    r = cross_validate(model, x, y, cv=cv, scoring={"auc": "roc_auc", "brier": "neg_brier_score", "acc": "accuracy"})
    return {k: float(np.mean(r[f"test_{k}"])) * (-1 if k == "brier" else 1) for k in ("auc", "brier", "acc")}


def main() -> None:
    df = load()
    y = df["outcome"].to_numpy()
    cv = RepeatedStratifiedKFold(n_splits=5, n_repeats=5, random_state=0)

    print(f"rows={len(df)}  positive rate={y.mean():.1%}  missing: "
          + ", ".join(f"{c}={df[c].isna().sum()}" for c in ["glucose", "blood_pressure", "bmi", "skin_thickness", "insulin"]))
    print("\nModel comparison (5x5 repeated stratified CV):")
    candidates = {
        "logistic, 4 usable inputs (chosen)": (lr(), FEATURES),
        "logistic, all 8 columns": (lr(), ALL8),
        "random forest, 4 inputs": (Pipeline([("impute", SimpleImputer(strategy="mean")),
                                              ("clf", RandomForestClassifier(300, min_samples_leaf=5, random_state=0))]), FEATURES),
        "grad. boosting, 4 inputs": (Pipeline([("clf", HistGradientBoostingClassifier(max_depth=3, learning_rate=0.05,
                                                                                    max_iter=150, random_state=0))]), FEATURES),
    }
    results = {}
    for name, (model, cols) in candidates.items():
        results[name] = cv_scores(model, df[cols], y, cv)
        r = results[name]
        print(f"  {name:<38} AUC {r['auc']:.3f}   Brier {r['brier']:.3f}   accuracy {r['acc']:.3f}")

    # How much worse is it when the person does not know their glucose? (glucose masked at test time)
    aucs, aucs_masked = [], []
    for train, test in cv.split(df[FEATURES], y):
        m = lr().fit(df.iloc[train][FEATURES], y[train])
        x_test = df.iloc[test][FEATURES]
        aucs.append(roc_auc_score(y[test], m.predict_proba(x_test)[:, 1]))
        x_masked = x_test.copy()
        x_masked["glucose"] = np.nan
        aucs_masked.append(roc_auc_score(y[test], m.predict_proba(x_masked)[:, 1]))
    print(f"\nChosen model AUC with glucose known: {np.mean(aucs):.3f}; glucose unknown: {np.mean(aucs_masked):.3f}")

    # Fit the final model on all data and export what the API needs to explain a prediction.
    model = lr().fit(df[FEATURES], y)
    imputer, scaler, clf = model.named_steps["impute"], model.named_steps["scale"], model.named_steps["clf"]
    score = model.predict_proba(df[FEATURES])[:, 1]
    print(f"Training-set accuracy (optimistic): {accuracy_score(y, score >= 0.5):.3f}  Brier {brier_score_loss(y, score):.3f}")

    ranges = {c: [float(df[c].min()), float(df[c].max())] for c in FEATURES}
    meta = {
        "version": VERSION,
        "trained_on": "Pima Indians Diabetes dataset (n=%d, women aged 21+ of Pima heritage)" % len(df),
        "n": int(len(df)),
        "positive_rate": float(y.mean()),
        "cv_auc": results["logistic, 4 usable inputs (chosen)"]["auc"],
        "cv_auc_without_glucose": float(np.mean(aucs_masked)),
        "cv_brier": results["logistic, 4 usable inputs (chosen)"]["brier"],
        "intercept": float(clf.intercept_[0]),
        "features": [
            {"name": c, **META[c], "mean": float(imputer.statistics_[i]), "scale": float(scaler.scale_[i]),
             "coef": float(clf.coef_[0][i]), "train_min": ranges[c][0], "train_max": ranges[c][1]}
            for i, c in enumerate(FEATURES)
        ],
    }
    joblib.dump(model, "risk_model.joblib")
    with open("risk_meta.json", "w") as f:
        json.dump(meta, f, indent=2)
    print("\nStandardised coefficients (log-odds per 1 SD):")
    for f_ in meta["features"]:
        print(f"  {f_['name']:<15} {f_['coef']:+.3f}   (SD {f_['scale']:.1f}, mean {f_['mean']:.1f})")
    print("Saved risk_model.joblib and risk_meta.json")


if __name__ == "__main__":
    main()
