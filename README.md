# Smart Health Assistant

Angular + Spring Boot + ML healthcare app: login and roles (patient / doctor), appointment booking,
an ML **doctor recommender**, a streaming **AI health chat** (Claude), and a **diabetes risk check**.
Educational project, not a medical device: nothing here is a diagnosis.

## Run it

Needs **JDK 21**, **Node 22.12+/24**, and **Python 3.11+**.

```bash
# 0. configuration (once): create your .env with the two required secrets
cp .env.example .env
{ echo "JWT_SECRET=$(openssl rand -base64 48)"
  echo "DATA_ENCRYPTION_KEY=$(openssl rand -base64 32)"
  echo "COOKIE_SECURE=false"; } >> .env   # false ONLY because local dev runs on plain http
# optional, for local demo doctors: SEED_DEMO_DATA=true and DEMO_DOCTOR_PASSWORD=<your own password>
# Back up DATA_ENCRYPTION_KEY: without it the encrypted data cannot be read.

# 1. ML service -> http://localhost:8000   (first time: create the venv and train the models)
cd ml-service
python3 -m venv .venv && .venv/bin/pip install -r requirements.txt
.venv/bin/python -I fetch_data.py data    # downloads the public Pima dataset (see data/README.md)
.venv/bin/python train.py                 # specialty classifier -> model.joblib, prints accuracy
.venv/bin/python train_risk.py            # diabetes risk model -> risk_model.joblib
.venv/bin/uvicorn app:app --port 8000

# 2. backend  -> http://localhost:8080   (reads ../.env automatically)
cd backend
mvn spring-boot:run

# 3. frontend -> http://localhost:4200
cd frontend
npm install && npm start
```

If you have several JDKs, point `JAVA_HOME` at a 21 before step 2.

## Configuration (environment variables)

Nothing deployment-specific or secret lives in the code. The backend reads these from real
environment variables or from `.env` (see [`.env.example`](.env.example) for the full annotated list).

| Variable | Purpose | Default |
|---|---|---|
| `JWT_SECRET` | **Required.** Signs login tokens, 32+ characters. The app refuses to start without it | none |
| `DATA_ENCRYPTION_KEY` | **Required.** 32 random bytes as base64; encrypts sensitive columns. Refuses to start without it | none |
| `DATA_ENCRYPTION_KEY_PREVIOUS` | Old key, only while rotating | empty |
| `JWT_EXPIRATION_MINUTES` | Access-token lifetime | `15` |
| `REFRESH_TOKEN_DAYS`, `REFRESH_REUSE_GRACE_SECONDS` | Session length; multi-tab grace window | `14`, `10` |
| `COOKIE_SECURE`, `COOKIE_SAMESITE` | Refresh-cookie flags (`false` only for plain-http dev) | `true`, `Strict` |
| `REQUIRE_HTTPS` | Redirect plain http to https | `false` (set `true` in production) |
| `SSL_ENABLED`, `SSL_KEY_STORE`, `SSL_KEY_STORE_PASSWORD` | Serve HTTPS from the app itself (see `scripts/dev-cert.sh`) | off |
| `FORWARD_HEADERS_STRATEGY` | `framework` ONLY behind a trusted reverse proxy | `none` |
| `AUTH_MAX_FAILED_LOGINS`, `AUTH_MAX_FAILED_LOGINS_PER_ACCOUNT`, `AUTH_LOCKOUT_MINUTES`, `AUTH_IP_MAX_REQUESTS`, `AUTH_IP_WINDOW_MINUTES` | Brute-force limits | `5`, `20`, `15`, `60`, `15` |
| `CORS_ORIGINS` | Browser origins allowed to call the API | `http://localhost:4200` |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Database | local H2 file |
| `ML_SERVICE_URL` | Python ML service | `http://localhost:8000` |
| `ANTHROPIC_API_KEY` | Enables the real AI chat; empty = demo mode | empty |
| `CHAT_PROVIDER`, `CHAT_MODEL`, `CHAT_EFFORT` | Chat provider, model, speed/quality | `auto`, `claude-opus-5-5`, `low` |
| `SWAGGER_ENABLED` | Serve interactive API docs | `true` (set `false` in production) |
| `SEED_DEMO_DATA`, `DEMO_DOCTOR_PASSWORD` | Create demo doctors (development only) | off, none |
| `SERVER_PORT` | Port | `8080` |

ML service (Python): `MODEL_PATH`, `RISK_MODEL_PATH`, `RISK_META_PATH` (file locations; defaults next to the code).

**Frontend:** the API URL is read at startup from `frontend/public/config.json` (served as
`/config.json`), not baked into the build, so one build runs in any environment. Change `apiUrl`
there, or generate that file from an environment variable in your deployment. Never put secrets in it.

The app works without the ML service except "Find a doctor", which shows a
"service unavailable" message with a retry button.

## Security

| Area | What is done |
|---|---|
| **Sessions** | A short-lived (15 min) access token held **in browser memory only**, plus a long-lived refresh token in an `HttpOnly`, `Secure`, `SameSite=Strict` cookie scoped to `/api/auth`. Nothing sensitive is in `localStorage`, so XSS cannot steal a login |
| **Refresh tokens** | Stored server-side only as a SHA-256 hash; **rotated on every use**; replaying an old token (stolen cookie) revokes the whole session family; a 10 s grace window stops two open tabs from triggering that falsely; `/api/auth/logout` revokes the session. The frontend refreshes silently on a 401 and shares one refresh between simultaneous requests |
| **CSRF** | Access token is a header (not ambient); the cookie endpoints also require `X-Requested-With` and are `SameSite=Strict` |
| **Login brute force** | Failures counted per (account, IP): 5 locks that pair for 15 min, so an attacker cannot lock the real owner out; 20 per account across all IPs stops botnets; per-IP request budget; unknown emails behave identically (no account enumeration); `429` + `Retry-After` |
| **Double-booking** | A unique database constraint on a `slot_key` that is only set while an appointment is BOOKED, so even simultaneous requests cannot book the same slot. Legacy rows are backfilled at startup |
| **Encryption at rest** | Appointment reasons and risk-check measurements are encrypted with AES-256-GCM (random nonce, tamper-detecting, key-id for rotation). Derived scores and levels stay in the clear so they can be sorted and filtered. This protects against a stolen database or backup, not against a compromised running server. Also encrypt the disk/database itself in production (e.g. managed PostgreSQL) |
| **HTTPS** | Run behind a TLS-terminating proxy (set `REQUIRE_HTTPS=true`, `FORWARD_HEADERS_STRATEGY=framework`) or serve TLS directly (`SSL_*`, `scripts/dev-cert.sh` makes a local certificate). HSTS is sent over HTTPS |
| **Headers** | `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, a locked-down `Content-Security-Policy` for the API, `Permissions-Policy`, `Cache-Control: no-store`; CORS allows only configured origins |

**Breaking change for existing databases:** the encrypted columns replace plaintext ones
(`risk_assessments` and `appointments.reason`), and new tables/columns appear. There is no migration tool yet
(Flyway is on the to-do list), so delete `backend/data/` once when upgrading a local development database.

## Diabetes risk check

```
Angular /risk -> Spring POST /api/risk/diabetes -> FastAPI /risk/diabetes -> saved per user
```

- **Model:** logistic regression on 4 inputs a person can actually know (age, 2-hour glucose,
  BMI, pregnancies), trained on the public Pima Indians Diabetes dataset (768 rows, downloaded
  once with `fetch_data.py` into `ml-service/data/`, not committed).
- **Explainable:** each input's contribution to the score is exact (coefficient x how far from
  average), shown as bars. Unknown inputs are filled with the average, contribute zero, and are
  labelled "unknown".
- **Choices backed by measurement** (5x5 repeated cross-validation, `train_risk.py`):

| Model | AUC |
|---|---|
| Logistic, 4 inputs (chosen) | 0.833 |
| Logistic, all 8 columns | 0.837 |
| Random forest, 4 inputs | 0.830 |
| Gradient boosting, 4 inputs | 0.831 |
| Chosen model without glucose | 0.732 |

  Simple beat complex here, so there was no reason to give up explainability. Blood pressure was
  tried and removed: no predictive value, and its coefficient was backwards.
- **Safety rules (not ML):** a 2-hour glucose of 200+ shows a "see a doctor" warning regardless
  of score; age-only requests are rejected; under-21s are rejected (outside the data).
- **Limits you should know:** the data is women of Pima heritage aged 21+, so results may not hold
  for men or other groups; the score is a similarity-to-training-data score, **not** a personal
  probability (the dataset is 35% positive, far above real-world prevalence); the "2-hour
  glucose" is from an oral glucose tolerance test, which most people do not have, so glucose is
  optional and accuracy drops when it is missing (the UI says so).
- **Privacy:** unlike chat, checks are saved (inputs, score, level) so users see history. They can
  delete one or all (`DELETE /api/risk/history[/{id}]`). The H2 file is not encrypted; use an
  encrypted database before storing real patient data.

## AI chat ("Aria")

```
Angular /chat --fetch stream--> Spring POST /api/chat/stream (SSE)
   1. rate limit (20 msgs/min/user)
   2. RedFlagDetector over the last 3 user messages -- emergency? stream a fixed
      message, the model is never called
   3. ChatLlm: ClaudeChatLlm (official Anthropic Java SDK, streaming) or MockChatLlm
   4. forwards only visible text chunks; thinking, refusals and truncation handled
```

- **Providers:** `CHAT_PROVIDER=auto` (default) uses Claude when `ANTHROPIC_API_KEY`
  (or `ANTHROPIC_AUTH_TOKEN`) is set, otherwise **demo mode** with scripted replies and
  a "Demo mode" badge in the UI. Force with `CHAT_PROVIDER=claude|mock`.
- **Model/cost:** default `CHAT_MODEL=claude-opus-5-5` with `CHAT_EFFORT=low` for fast
  replies. Chat is cheap per message, but cheaper models (e.g. `claude-sonnet-5-5`,
  `claude-haiku-5-5`) are one env var away; set `CHAT_EFFORT=` (empty) for models
  without effort support.
- **Privacy:** the conversation lives only in the browser's memory (cleared on logout,
  gone on refresh). The server stores nothing and never logs message text. Symptoms
  go to "Find a doctor" through memory, not the URL.
- **Prompt:** `ChatPrompts.java` (plain text replies, no diagnoses/doses, escalate
  emergencies, treats user text as data, not instructions).

**What was and was not tested:** the streaming path through the SDK was tested against
a local fake server that emits Claude's documented streaming events (text, hidden
thinking, refusal, max_tokens, HTTP 500). It has **not** been run against the real
Claude API (no key was available), so do a real smoke test with your key. Server-side
refusal fallbacks are not enabled; a refusal shows a friendly notice instead.

## How the doctor recommender works

```
Angular /recommend -> Spring POST /api/recommendations
   1. RedFlagDetector (regex rules)  -- emergency? answer immediately, model never runs
   2. MlClient -> FastAPI /predict   -- TF-IDF + logistic regression -> top specialties
   3. RecommendationService          -- ranks real doctors by specialty confidence,
                                        then by fewest upcoming bookings
```

- **Safety first:** chest pain with breathlessness/arm pain/sweating, stroke signs,
  self-harm language, severe bleeding or breathing trouble, etc. return an emergency
  message and no doctors. These are plain rules in `RedFlagDetector.java`, not ML.
- **Low confidence** (top guess under 35%) leads with General Medicine doctors.
- **Privacy:** symptom text is never logged, and it goes to the booking form through
  an in-memory service (`booking-draft.service.ts`), never the URL.

### Model quality: read this before trusting it

The training data is a **small hand-made demo set** (symptom phrases combined into
sentences), not clinical data. Last training run:

| Check | Result |
|---|---|
| Held-out generated sentences | 96% (optimistic: same style as training) |
| 30 hand-written sentences used while tuning | 100% top-1 |
| 24 hand-written sentences never tuned on | 96% top-1 (23/24), 100% top-3 |

The 24-sentence test is small and written in language close to the training
vocabulary, so real-world accuracy will be lower. To improve it: use a real
labelled dataset, or swap TF-IDF for sentence embeddings. Re-run `train.py`
after any change to `dataset.py`; it prints the same table.

Demo doctors (12 across 10 specialties, e.g. `asha.verma@clinic.test`, `priya.nair@clinic.test`) are
created only when you set `SEED_DEMO_DATA=true` and your own `DEMO_DOCTOR_PASSWORD`.
Patients: use the Register page.

Data lives in `backend/data/` (H2 file DB). Delete that folder to reset.

## API documentation (OpenAPI / Swagger)

With the backend running: **http://localhost:8080/swagger-ui.html** (interactive; call
`POST /api/auth/login`, copy the token, click **Authorize**) and the raw spec at
`http://localhost:8080/v3/api-docs`. Set `SWAGGER_ENABLED=false` in production.

## API

List endpoints return `{ content, page, size, totalElements, totalPages, hasNext }`.
`size` is 1-100 and `sort=field,asc|desc` only accepts a per-endpoint whitelist (anything else is a 400),
so clients can never sort by arbitrary columns.

| Method | Path | Who |
|---|---|---|
| POST | `/api/auth/register`, `/api/auth/login` | public |
| GET | `/api/auth/me` | any signed-in user |
| GET | `/api/doctors?specialty=&q=&page=&size=&sort=` | any signed-in user (paged, searchable) |
| GET | `/api/appointments?status=&from=&to=&page=&size=&sort=` | own bookings (patient) / bookings with me (doctor); paged and filterable |
| GET | `/api/appointments/summary` | counts per status + next upcoming (dashboard) |
| POST | `/api/appointments` | patient |
| PATCH | `/api/appointments/{id}` | patient (reschedule and/or edit the reason; partial update) |
| POST | `/api/recommendations` | patient |
| POST | `/api/risk/diabetes` | patient (saves the check) |
| GET / DELETE | `/api/risk/history?level=&page=&size=&sort=`, `/api/risk/history/{id}` | patient (own records only; paged) |
| GET | `/api/chat/status` | patient (which provider is active) |
| POST | `/api/chat/stream` | patient (Server-Sent Events) |
| PATCH | `/api/appointments/{id}/cancel` | patient or doctor on it |
| PATCH | `/api/appointments/{id}/complete` | doctor on it |

Errors always look like `{ timestamp, status, message, fieldErrors? }`.

## Automated tests (218)

| Suite | Count | Run | What it covers |
|---|---|---|---|
| Backend: JUnit 5 + MockMvc + H2 | 142 | `cd backend && mvn test` | Every endpoint through the real security filters and JPA: auth, permissions and data isolation, booking/conflicts/reschedule, paging, filtering, sorting whitelist, summary, risk checks, recommender, streaming chat, rate limit, OpenAPI spec. Plus unit tests for the emergency rules, JWT, paging helper |
| ML service: pytest | 32 | `cd ml-service && .venv/bin/pip install -r requirements-dev.txt && .venv/bin/python -m pytest` | Both models: expected predictions, explanation adds up exactly to the score, every factor pushes the right way, quality floors (a bad retrain fails here) |
| Angular: Karma/Jasmine | 44 | `cd frontend && ng test --watch=false --browsers=ChromeHeadless` (needs Chrome) | Paged appointment store (stale responses, silent refresh, step-back), chat store (streaming, stop, retry, emergencies), guards, validators, error parser |

The backend tests replace only the Python ML service with a mock, so no network, API key or
running servers are needed. I checked the tests really bite by temporarily breaking the code
(removing the visibility filter, removing the double-booking check): both made tests fail.
Not covered: browser end-to-end tests (Playwright/Cypress), load tests, and the real Claude API.

## Where each Angular concept lives (`frontend/src/app`)

| Concept | File |
|---|---|
| Standalone components, lazy routes | `app.routes.ts` |
| App-wide providers, app initializer | `app.config.ts` |
| Services + DI (`inject`) | `core/services/*` |
| Signals, `computed`, store pattern | `core/services/appointment.store.ts` |
| Auth state as signals | `core/services/auth.service.ts` |
| HTTP interceptors (token, 401/5xx) | `core/interceptors/*` |
| Route guards (auth, guest, role) | `core/guards/auth.guards.ts` |
| Typed API models | `core/models/models.ts` |
| Reactive forms, validators, cross-field | `features/auth/register.component.ts`, `shared/validators/validators.ts` |
| Conditional validation | `register.component.ts` constructor |
| RxJS `debounceTime` + `toSignal` | `features/appointments/appointment-form.component.ts` |
| Loading / error / empty states | `appointment-list.component.html`, `dashboard.component.html` |
| `@if` / `@for` / `@switch` | every `.html` |
| `input()` / `output()` | `shared/components/status-badge`, `confirm-dialog` |
| Custom pipe / directive | `shared/pipes/initials.pipe.ts`, `shared/directives/autofocus.directive.ts` |
| Route params to inputs | `login.component.ts` (`returnUrl`) |

## Before production

- Set `JWT_SECRET` (no default), `CORS_ORIGINS`, a real `DB_URL` and `SWAGGER_ENABLED=false` as environment variables; leave `SEED_DEMO_DATA` off.
- Swap H2 for PostgreSQL, and move the API URL into environment files.
- The JWT sits in `localStorage` (simple, but readable by XSS); consider httpOnly cookies.
- Add login rate limiting (only the chat is rate limited today).
