# Training data

`pima.csv` (not committed; it is git-ignored) is the Pima Indians Diabetes dataset (768 rows) that you download once with `fetch_data.py`
from OpenML (dataset "diabetes", v1, https://www.openml.org/d/37). It originates from the
National Institute of Diabetes and Digestive and Kidney Diseases. Check the original source's
terms before redistributing it outside this project.

Known limits: all participants are women of Pima heritage aged 21 and over, and a value of 0 in
glucose / blood pressure / BMI / skin thickness / insulin means "missing", not zero.
Download with: `.venv/bin/python -I fetch_data.py data` (from `ml-service/`). Check the original source's terms before redistributing the data.
