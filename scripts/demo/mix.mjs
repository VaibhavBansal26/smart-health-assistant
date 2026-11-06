// Lays each narration clip at its recorded start time on top of the raw screen recording -> docs/demo/demo.mp4
import { readFileSync, rmSync } from 'node:fs';
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
filters.push(`${timeline.map((_, i) => `[a${i}]`).join('')}amix=inputs=${timeline.length}:normalize=0[voice]`);
execFileSync('ffmpeg', ['-y', '-loglevel', 'error', ...inputs, '-filter_complex', filters.join(';'),
  '-map', '0:v', '-map', '[voice]', '-c:v', 'copy',
  '-c:a', 'aac', '-b:a', '128k', '-t', execFileSync('ffprobe', ['-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', fixed]).toString().trim(),
  '-movflags', '+faststart', join(out, 'demo.mp4')]);
rmSync(join(out, 'demo-raw.mp4'));
rmSync(fixed);
rmSync(join(out, 'narration-timeline.json'));
console.log('wrote docs/demo/demo.mp4');
