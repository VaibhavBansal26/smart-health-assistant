"""Synthesises an original upbeat EDM-style backing loop (no samples, so no licensing questions).

128 BPM, A minor (Am - F - C - G), four-on-the-floor kick, off-beat bass, claps, hats and a plucked arpeggio,
with the bass and arpeggio ducked on every kick for the classic "pumping" feel. Output: .audio/music.wav (about 31 s,
meant to be looped and ducked under the voiceover by mix.mjs).

Run with any Python that has numpy, e.g.  ml-service/.venv/bin/python scripts/demo/music.py
"""
import wave
from pathlib import Path

import numpy as np

SR = 44100
BPM = 128
BEAT = 60 / BPM
BARS = 16
N = int(SR * BEAT * 4 * BARS)
rng = np.random.default_rng(7)
t_all = np.arange(N) / SR


def hz(midi):
    return 440.0 * 2 ** ((midi - 69) / 12)


def place(track, sig, start_s, gain=1.0):
    i = int(start_s * SR)
    j = min(N, i + len(sig))
    if i < N:
        track[i:j] += gain * sig[: j - i]


def kick():
    t = np.arange(int(SR * 0.45)) / SR
    freq = 50 + 110 * np.exp(-t * 28)
    phase = 2 * np.pi * np.cumsum(freq) / SR
    return np.sin(phase) * np.exp(-t * 7) * (1 + 0.6 * np.exp(-t * 80))


def clap():
    t = np.arange(int(SR * 0.22)) / SR
    noise = rng.standard_normal(len(t))
    noise = noise - np.convolve(noise, np.ones(8) / 8, mode='same')  # crude high-pass
    env = np.exp(-t * 22) + 0.7 * np.exp(-((t - 0.012) ** 2) * 9000)
    return noise * env * 0.5


def hat(open_=False):
    t = np.arange(int(SR * (0.16 if open_ else 0.05))) / SR
    noise = rng.standard_normal(len(t))
    noise = noise - np.convolve(noise, np.ones(4) / 4, mode='same')
    return noise * np.exp(-t * (18 if open_ else 70)) * 0.22


def saw(freq, dur, detune=0.003):
    t = np.arange(int(SR * dur)) / SR
    out = np.zeros_like(t)
    for d in (-detune, 0, detune):
        out += 2 * ((t * freq * (1 + d)) % 1) - 1
    return out / 3, t


def lowpass(x, k):
    return np.convolve(x, np.ones(k) / k, mode='same')


chords = [(57, [57, 60, 64]), (53, [53, 57, 60]), (48, [48, 52, 55]), (55, [55, 59, 62])]  # Am F C G (root, triad)
drums = np.zeros(N)
bass = np.zeros(N)
arp = np.zeros(N)
pad = np.zeros(N)

k = kick()
c = clap()
for bar in range(BARS):
    root, triad = chords[(bar // 2) % 4]
    b0 = bar * 4 * BEAT
    intro = bar < 2  # first two bars: just pads and hats, then the beat drops in
    for beat in range(4):
        tb = b0 + beat * BEAT
        if not intro:
            place(drums, k, tb, 1.0)
            if beat in (1, 3):
                place(drums, c, tb, 0.8)
            place(drums, hat(True), tb + BEAT / 2, 1.0)
        place(drums, hat(), tb + BEAT / 4, 0.6)
        place(drums, hat(), tb + 3 * BEAT / 4, 0.6)
        if not intro:
            # off-beat sub bass
            s, tt = saw(hz(root - 12), BEAT * 0.45, 0.0)
            bass_note = lowpass(s, 40) * np.exp(-tt * 3) * 0.9
            place(bass, bass_note, tb + BEAT / 2, 1.0)
        # 16th-note pluck arpeggio over the triad
        for step in range(4):
            note = triad[(beat * 4 + step) % 3] + (12 if (step % 2) else 24)
            s, tt = saw(hz(note), BEAT / 4 * 0.9)
            place(arp, lowpass(s, 6) * np.exp(-tt * 14) * 0.5, tb + step * BEAT / 4, 1.0)
    for n in triad:  # sustained pad for the whole bar
        s, tt = saw(hz(n), BEAT * 4, 0.006)
        env = np.minimum(1, tt / 0.4) * np.minimum(1, (BEAT * 4 - tt) / 0.4)
        place(pad, lowpass(s, 12) * env * 0.18, b0, 1.0)

# sidechain "pump": melodic parts duck for ~0.25 s after each kick
duck = np.ones(N)
kick_times = [bar * 4 * BEAT + beat * BEAT for bar in range(2, BARS) for beat in range(4)]
for kt in kick_times:
    i = int(kt * SR)
    n = int(SR * 0.28)
    seg = np.arange(min(n, N - i)) / SR
    duck[i:i + len(seg)] = np.minimum(duck[i:i + len(seg)], 0.25 + 0.75 * (seg / 0.28) ** 1.5)
mix = drums + (bass * 1.1 + arp * 0.8 + pad) * duck
# gentle fade at the loop boundary so it repeats without a click
f = int(SR * 0.01)
mix[:f] *= np.linspace(0, 1, f)
mix[-f:] *= np.linspace(1, 0, f)
mix = np.tanh(mix * 0.9)
mix = mix / np.max(np.abs(mix)) * 0.9

out = Path(__file__).parent / '.audio'
out.mkdir(exist_ok=True)
with wave.open(str(out / 'music.wav'), 'wb') as w:
    w.setnchannels(1)
    w.setsampwidth(2)
    w.setframerate(SR)
    w.writeframes((mix * 32767).astype(np.int16).tobytes())
print(f'wrote {out / "music.wav"} ({N / SR:.1f}s)')
