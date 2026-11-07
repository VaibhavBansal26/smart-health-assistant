// Lays each narration clip at its recorded start time on top of the raw screen recording -> docs/demo/demo.mp4
import { existsSync, readFileSync, rmSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const out = join(here, '..', '..', 'docs', 'demo');
const { totalMs, lines: timeline } = JSON.parse(readFileSync(join(out, 'narration-timeline.json'), 'utf8'));
const OFFSET_MS = Number(process.env.AUDIO_OFFSET_MS ?? 0); // nudge if the voice drifts from the picture
// The screencast has no duration header and a variable frame rate: normalise it first (25 fps), then mix.
const fixed = join(out, 'demo-fixed.mp4');
execFileSync('ffmpeg', ['-y', '-loglevel', 'error', '-i', join(out, 'demo-raw.mp4'), '-r', '25', '-c:v', 'libx264', '-crf', '30',
  '-preset', 'slow', '-pix_fmt', 'yuv420p', '-an', fixed]);
const inputs = ['-i', fixed];
// The screencast plays slightly slower than the wall clock the timeline was taken on, so stretch the clip positions to match.
const videoMs = Number(execFileSync('ffprobe', ['-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', fixed]).toString().trim()) * 1000;
const SCALE = videoMs / totalMs;
console.log(`video ${(videoMs / 1000).toFixed(1)}s vs wall clock ${(totalMs / 1000).toFixed(1)}s -> scale ${SCALE.toFixed(3)}`);
const filters = [];
timeline.forEach((t, i) => {
  inputs.push('-i', join(here, '.audio', `${t.id}.mp3`));
  const ms = Math.max(0, Math.round(t.startMs * SCALE + OFFSET_MS));
  filters.push(`[${i + 1}:a]adelay=${ms}|${ms}[a${i}]`);
});
// Voice track: all narration clips at their start times
filters.push(`${timeline.map((_, i) => `[a${i}]`).join('')}amix=inputs=${timeline.length}:normalize=0[voice]`);

// Optional background music (run music.py first): looped, faded in/out, and ducked whenever the narrator speaks
const musicFile = join(here, '.audio', 'music.wav');
const MUSIC_VOLUME = Number(process.env.MUSIC_VOLUME ?? 0.35); // 1.0 = as synthesised; lower is quieter
const videoSec = videoMs / 1000;
let voiceLabel = '[voice]';
if (existsSync(musicFile) && MUSIC_VOLUME > 0) {
  inputs.push('-stream_loop', '-1', '-i', musicFile);
  const mi = timeline.length + 1;
  filters.push('[voice]asplit=2[vkey][vmix]');
  filters.push(`[${mi}:a]atrim=0:${videoSec},asetpts=PTS-STARTPTS,volume=${MUSIC_VOLUME},afade=t=in:d=2,afade=t=out:st=${Math.max(0, videoSec - 4)}:d=4[music]`);
  // sidechain: the voice (key) pushes the music down by ~14 dB while it is talking, and it swells back in the pauses
  filters.push('[music][vkey]sidechaincompress=threshold=0.02:ratio=4:attack=25:release=500:makeup=1[musicducked]');
  filters.push('[vmix][musicducked]amix=inputs=2:normalize=0:duration=longest[final]');
  voiceLabel = '[final]';
}
execFileSync('ffmpeg', ['-y', '-loglevel', 'error', ...inputs, '-filter_complex', filters.join(';'),
  '-map', '0:v', '-map', voiceLabel, '-c:v', 'copy',
  '-c:a', 'aac', '-b:a', '160k', '-t', String(videoSec),
  '-movflags', '+faststart', join(out, 'demo.mp4')]);
rmSync(join(out, 'demo-raw.mp4'));
rmSync(fixed);
rmSync(join(out, 'narration-timeline.json'));
console.log('wrote docs/demo/demo.mp4');
