# Demo: every feature, start to finish

## Watch the video

**[docs/demo/demo.mp4](docs/demo/demo.mp4)** (about 4.5 minutes, with voiceover, background music and captions): admin, patient and doctor
journeys, the ML features, security, and a tech-stack outro. It is recorded from the live app by
[`scripts/demo/record.mjs`](scripts/demo/record.mjs); the narration is spoken with ElevenLabs text-to-speech
from the lines in [`scripts/demo/narration.json`](scripts/demo/narration.json).

> The chat in the video and screenshots is **real Claude** (`ANTHROPIC_API_KEY` set). Without a key the app falls back to a
> clearly labelled scripted demo mode.

### Re-record it

```bash
# 1. Run the three servers (see README). Use a fresh database, MAIL_MODE=log, SEED_DEMO_DATA=true,
#    ADMIN_EMAIL / ADMIN_PASSWORD set, and ANTHROPIC_API_KEY (or CHAT_PROVIDER=mock for the scripted chat).
cd scripts/demo && npm install

# 2. Voiceover: needs ELEVENLABS_API_KEY and ELEVENLABS_VOICE_ID (environment or the git-ignored .env at the repo root)
node narration.mjs                      # writes .audio/*.mp3 and their durations (git-ignored)

#    Optional EDM backing track (synthesised, original; ducks under the voice automatically):
../../ml-service/.venv/bin/python music.py   # writes .audio/music.wav; MUSIC_VOLUME=0 for no music, higher for louder

# 3. Record the app (each scene waits for its narration line), then mix the audio onto it
VIDEO=1 BACKEND_LOG=/tmp/backend.log ADMIN_PASSWORD=<...> node record.mjs
node mix.mjs                            # -> docs/demo/demo.mp4 (needs ffmpeg)

# Screenshots only (the images below), no audio:
BACKEND_LOG=/tmp/backend.log ADMIN_PASSWORD=<...> node record.mjs
```

The script creates a new doctor and patient with unique emails, so use a fresh database for each run.

## The tour (screenshots)

### 1. Admin invites a doctor
Doctors cannot register themselves. An admin (created from `ADMIN_*` env vars) adds one, who gets an invite email
and chooses their own password. Deactivating a doctor ends their sessions and hides them from booking.

![admin](docs/demo/admin-doctors.png)
![add doctor](docs/demo/admin-add-doctor.png)
![invited](docs/demo/admin-doctor-invited.png)

### 2. Patient signs up and verifies email
Until the emailed link is used, a banner explains what is locked.

![register](docs/demo/register.png)
![unverified](docs/demo/dashboard-unverified-banner.png)
![verified](docs/demo/email-verified.png)

### 3. Find a doctor (ML recommender) and emergency rules
Free-text symptoms go to a Python model that predicts the specialty; real doctors are ranked by it.
Emergency wording is caught by plain rules, not ML, and shows a warning instead of doctors.

![recommend](docs/demo/recommend-result.png)
![emergency](docs/demo/recommend-emergency.png)

### 4. Diabetes risk check
A model trained on the public Pima dataset returns a score and shows which inputs pushed it up or down.
Results are encrypted at rest and can be deleted.

![risk](docs/demo/risk-result.png)
![history](docs/demo/risk-history.png)

### 5. Chat with Aria
Streaming replies from Claude over Server-Sent Events, a stop button, retry on error, and an emergency override.

![chat](docs/demo/chat-reply.png)
![chat emergency](docs/demo/chat-emergency.png)

### 6. Book and reschedule
Double-booking is blocked by a database constraint. Lists are paged and filterable.

![book](docs/demo/book-form.png)
![list](docs/demo/appointments-list.png)
![reschedule](docs/demo/reschedule-dialog.png)

### 7. Private messages between patient and doctor
Each appointment has its own thread. Only the two people on it can read or write (everyone else gets a 404),
text is encrypted in the database, new messages appear within a few seconds, and a cancelled appointment becomes read-only.

![patient writes](docs/demo/messages-patient-sent.png)
![doctor reads and replies](docs/demo/messages-doctor-replied.png)

### 8. The doctor's side
The doctor sees only their own bookings, reads the patient's message, replies and marks the visit completed.

![doctor dashboard](docs/demo/doctor-dashboard.png)
![doctor appointments](docs/demo/doctor-appointments.png)
![completed](docs/demo/doctor-completed.png)

### 9. The patient sees the reply
![reply](docs/demo/messages-patient-reply.png)

### 10. Password reset and brute-force protection
The reset response is identical whether or not the email exists, the link works once, and a reset signs out every session.
Repeated wrong passwords lock that account and address pair for 15 minutes.

![forgot](docs/demo/forgot-password-sent.png)
![reset](docs/demo/reset-password.png)
![locked](docs/demo/login-locked.png)

## Also in the app (not shown)
Swagger UI at `/swagger-ui.html`, HTTPS with HSTS (`scripts/dev-cert.sh`), silent token refresh, and key rotation for
encrypted data. See the README.
