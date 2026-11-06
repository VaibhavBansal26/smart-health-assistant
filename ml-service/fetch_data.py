"""One-off download of the Pima Indians Diabetes dataset (OpenML name 'diabetes', version 1).

Usage:  .venv/bin/python -I fetch_data.py data

Writes <output-dir>/pima.csv with readable column names, so training afterwards
works offline. Source: OpenML (https://www.openml.org/d/37), originally from the
National Institute of Diabetes and Digestive and Kidney Diseases.
Run with -I (isolated mode) so nothing from the current directory is imported.
"""
import shutil
import sys
from pathlib import Path

from sklearn.datasets import fetch_openml

out_dir = Path(sys.argv[1])
if out_dir.exists() and any(p.name != "README.md" for p in out_dir.iterdir()):
    sys.exit(f"{out_dir} already contains files; refusing to mix downloaded data with other files")
out_dir.mkdir(parents=True, exist_ok=True)

ds = fetch_openml(name="diabetes", version=1, as_frame=True, data_home=str(out_dir / "_openml_cache"))
df = ds.frame.rename(columns={
    "preg": "pregnancies", "plas": "glucose", "pres": "blood_pressure", "skin": "skin_thickness",
    "insu": "insulin", "mass": "bmi", "pedi": "pedigree", "age": "age", "class": "outcome",
})
df["outcome"] = (df["outcome"] == "tested_positive").astype(int)
df.to_csv(out_dir / "pima.csv", index=False)
shutil.rmtree(out_dir / "_openml_cache", ignore_errors=True)  # keep only the CSV
print(f"rows={len(df)} columns={list(df.columns)} positive_rate={df['outcome'].mean():.3f}")
