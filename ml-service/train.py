"""Trains the symptom -> specialty classifier and saves it to model.joblib.

Run:  .venv/bin/python train.py
"""
import joblib
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.pipeline import FeatureUnion
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline

from dataset import DEV_SET, TEST_SET, generate_training_data

MODEL_PATH = "model.joblib"


def build_pipeline() -> Pipeline:
    return Pipeline([
        # word 1-2 grams catch phrases like "chest pain"; char n-grams catch variants
        # ("headaches" ~ "headache", "tingles" ~ "tingling") and typos.
        ("features", FeatureUnion([
            ("word", TfidfVectorizer(ngram_range=(1, 2), sublinear_tf=True, strip_accents="unicode")),
            ("char", TfidfVectorizer(analyzer="char_wb", ngram_range=(3, 5), sublinear_tf=True, strip_accents="unicode")),
        ])),
        ("clf", LogisticRegression(max_iter=2000, C=4.0)),
    ])


def report(model: Pipeline, name: str, data: list[tuple[str, str]], show_misses: bool = False) -> None:
    classes = list(model.classes_)
    proba = model.predict_proba([t for t, _ in data])
    top1 = top3 = 0
    for (text, truth), p in zip(data, proba):
        ranked = [classes[i] for i in p.argsort()[::-1]]
        top1 += ranked[0] == truth
        top3 += truth in ranked[:3]
        if show_misses and ranked[0] != truth:
            print(f"    MISS expected={truth:<17} got={ranked[0]:<17} conf={p.max():.2f} | {text}")
    n = len(data)
    print(f"{name}: n={n}  top-1 {top1 / n:.0%}  top-3 {top3 / n:.0%}")


def main() -> None:
    texts, labels = generate_training_data()
    x_train, x_val, y_train, y_val = train_test_split(texts, labels, test_size=0.2, random_state=1, stratify=labels)

    model = build_pipeline().fit(x_train, y_train)
    print(f"Validation accuracy (template data, optimistic): {model.score(x_val, y_val):.3f}")

    report(model, "DEV  (hand-written, used while tuning vocabulary)", DEV_SET)
    report(model, "TEST (hand-written, never tuned on)", TEST_SET, show_misses=True)

    # Final model: retrain on everything.
    final = build_pipeline().fit(texts, labels)
    joblib.dump(final, MODEL_PATH)
    print(f"Saved {MODEL_PATH} with classes: {[str(c) for c in final.classes_]}")


if __name__ == "__main__":
    main()
