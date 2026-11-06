// Turns narration.json into mp3 files with ElevenLabs text-to-speech and writes durations.json.
// Needs ELEVENLABS_API_KEY and ELEVENLABS_VOICE_ID (environment, or the repo's git-ignored .env).
import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const AUDIO = join(here, '.audio');
function fromDotEnv(name) {
  const f = join(here, '..', '..', '.env');
  if (!existsSync(f)) return undefined;
  const line = readFileSync(f, 'utf8').split('\n').find((l) => l.startsWith(name + '='));
  return line?.slice(name.length + 1).trim();
}
const KEY = process.env.ELEVENLABS_API_KEY ?? fromDotEnv('ELEVENLABS_API_KEY');
const VOICE = process.env.ELEVENLABS_VOICE_ID ?? fromDotEnv('ELEVENLABS_VOICE_ID');
if (!KEY || !VOICE) { console.error('Set ELEVENLABS_API_KEY and ELEVENLABS_VOICE_ID.'); process.exit(1); }

mkdirSync(AUDIO, { recursive: true });
const lines = JSON.parse(readFileSync(join(here, 'narration.json'), 'utf8'));
const durations = {};
let previous = '';
for (const line of lines) {
  const file = join(AUDIO, `${line.id}.mp3`);
  const res = await fetch(`https://api.elevenlabs.io/v1/text-to-speech/${VOICE}?output_format=mp3_44100_128`, {
    method: 'POST',
    headers: { 'xi-api-key': KEY, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      text: line.text, model_id: 'eleven_multilingual_v2',
      previous_text: previous || undefined, // keeps the tone consistent between lines
    }),
  });
  if (!res.ok) { console.error(`ElevenLabs ${res.status} for "${line.id}": ${(await res.text()).slice(0, 200)}`); process.exit(1); }
  writeFileSync(file, Buffer.from(await res.arrayBuffer()));
  durations[line.id] = Number(execFileSync('ffprobe', ['-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', file]).toString());
  previous = line.text;
  console.log(`  ${line.id}: ${durations[line.id].toFixed(1)}s`);
}
writeFileSync(join(AUDIO, 'durations.json'), JSON.stringify(durations, null, 2));
console.log(`Total narration: ${Object.values(durations).reduce((a, b) => a + b, 0).toFixed(0)}s`);
