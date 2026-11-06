// Walks through every feature of the running app and saves screenshots to docs/demo/.
// VIDEO=1 records docs/demo/demo-raw.mp4 with captions and slower typing, timing each scene to the voiceover
// produced by narration.mjs (run that first), and writes docs/demo/narration-timeline.json for mix.mjs.
// Needs: ML service, backend (MAIL_MODE=log, SEED_DEMO_DATA=true, ADMIN_* set) and frontend running.
// Config via env: CHROME_BIN, BACKEND_LOG (file the backend logs to), APP_URL, API_URL,
//                 ADMIN_EMAIL, ADMIN_PASSWORD, DEMO_DOCTOR_PASSWORD.
import puppeteer from 'puppeteer-core';
import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const OUT = join(here, '..', '..', 'docs', 'demo');
const APP = process.env.APP_URL ?? 'http://localhost:4200';
const API = process.env.API_URL ?? 'http://localhost:8080/api';
const LOG = process.env.BACKEND_LOG;
const CHROME = process.env.CHROME_BIN ?? '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const ADMIN = { email: process.env.ADMIN_EMAIL ?? 'admin@demo.test', password: process.env.ADMIN_PASSWORD };
if (!LOG || !ADMIN.password) { console.error('Set BACKEND_LOG and ADMIN_PASSWORD (see header).'); process.exit(1); }

const run = Date.now().toString(36);
const PATIENT = { name: 'Nora Vega', email: `nora.${run}@demo.test`, password: 'Patient-Pass-123' };
const NEW_DOC = { name: 'Dr. Ivy Chen', email: `ivy.${run}@demo.test`, specialty: 'Dermatology', password: 'Doctor-Pass-12345' };

const VIDEO = process.env.VIDEO === '1';
mkdirSync(OUT, { recursive: true });
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
let n = 0;

const browser = await puppeteer.launch({ executablePath: CHROME, headless: 'new', defaultViewport: { width: 1280, height: 820 } });
const page = await browser.newPage();
// Caption bar shown in the video; the text lives in window.name so it survives page loads.
await page.evaluateOnNewDocument(() => {
  const draw = () => {
    if (!window.name.startsWith('cap:') || document.getElementById('demo-cap')) return;
    const d = document.createElement('div'); d.id = 'demo-cap'; d.textContent = window.name.slice(4);
    d.style.cssText = 'position:fixed;left:0;right:0;bottom:0;z-index:99999;padding:14px 24px;font:600 20px system-ui,sans-serif;color:#fff;background:rgba(15,23,42,.88);text-align:center;pointer-events:none';
    document.body.appendChild(d);
  };
  document.addEventListener('DOMContentLoaded', draw);
  new MutationObserver(draw).observe(document, { childList: true, subtree: true });
});
const NARR = JSON.parse(readFileSync(join(here, 'narration.json'), 'utf8'));
const DUR = VIDEO ? JSON.parse(readFileSync(join(here, '.audio', 'durations.json'), 'utf8')) : {};
const timeline = [];
let t0 = 0;
let speakingUntil = 0;
/** Starts a scene: waits for the previous line to finish, captions it, and notes when its audio must start. */
async function say(id) {
  if (!VIDEO) return;
  const wait = speakingUntil - Date.now();
  if (wait > 0) await sleep(wait + 350);
  const line = NARR.find((l) => l.id === id);
  await caption(line.caption);
  timeline.push({ id, startMs: Date.now() - t0 });
  speakingUntil = Date.now() + DUR[id] * 1000;
}
async function card(html) {
  await page.evaluate((h) => { document.body.innerHTML = h; document.getElementById('demo-cap')?.remove(); }, html);
}
async function caption(text) {
  if (!VIDEO) return;
  await page.evaluate((t) => { window.name = 'cap:' + t; document.getElementById('demo-cap')?.remove(); const e = document.createElement('div'); e.id = 'demo-cap'; e.textContent = t; e.style.cssText = 'position:fixed;left:0;right:0;bottom:0;z-index:99999;padding:14px 24px;font:600 20px system-ui,sans-serif;color:#fff;background:rgba(15,23,42,.88);text-align:center;pointer-events:none'; document.body.appendChild(e); }, text);
}

async function shot(name) {
  if (VIDEO) { await sleep(2200); return; }
  await sleep(500);
  const file = `${name}.png`; n++;
  await page.screenshot({ path: join(OUT, file) });
  console.log('  saved', file);
}
const go = async (path) => { await page.goto(APP + path, { waitUntil: 'networkidle0' }); };
async function clickText(text, selector = 'button, a') {
  const ok = await page.evaluate((t, sel) => {
    const el = [...document.querySelectorAll(sel)].find((e) => e.textContent.trim().toLowerCase().includes(t.toLowerCase()) && !e.disabled);
    if (el) { el.click(); return true; } return false;
  }, text, selector);
  if (!ok) throw new Error(`No clickable "${text}"`);
}
async function clickExact(text, selector = 'button, a') {
  const ok = await page.evaluate((t, sel) => {
    const el = [...document.querySelectorAll(sel)].find((e) => e.textContent.trim() === t && !e.disabled);
    if (el) { el.click(); return true; } return false;
  }, text, selector);
  if (!ok) throw new Error(`No clickable "${text}"`);
}
async function type(sel, value) { await page.click(sel, { clickCount: 3 }); await page.type(sel, value, { delay: VIDEO ? 45 : 0 }); }
async function setValue(sel, value) {
  await page.$eval(sel, (el, v) => { el.value = v; el.dispatchEvent(new Event('input', { bubbles: true })); el.dispatchEvent(new Event('change', { bubbles: true })); }, value);
}
function lastEmailToken(kind) {
  const hits = [...readFileSync(LOG, 'utf8').matchAll(new RegExp(`${kind}\\?token=([A-Za-z0-9_-]+)`, 'g'))];
  if (!hits.length) throw new Error(`No ${kind} link in the backend log`);
  return hits.at(-1)[1];
}
async function waitFor(fn, what, ms = 20000) {
  const t0 = Date.now();
  while (Date.now() - t0 < ms) { try { if (await page.evaluate(fn)) return; } catch {} await sleep(300); }
  throw new Error(`Timed out waiting for ${what}`);
}
async function signIn(email, password) {
  await go('/login');
  await type('#email', email); await type('#password', password);
  await clickText('Sign in', 'button[type=submit]');
  await page.waitForFunction(() => !location.pathname.startsWith('/login'), { timeout: 15000 });
  await sleep(1200);
}
async function signOut() { await clickText('Sign out'); await sleep(1000); }
const tomorrow = (h) => { const d = new Date(Date.now() + 86400000); d.setHours(h, 0, 0, 0); const p = (x) => String(x).padStart(2, '0'); return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:00`; };

let rec;
if (VIDEO) {
  await go('/login');
  rec = await page.screencast({ path: join(OUT, 'demo-raw.mp4') });
  t0 = Date.now();
}
try {
  await say('intro');
  if (VIDEO) {
    await card(`<div style="height:100vh;display:grid;place-items:center;background:linear-gradient(135deg,#0f766e,#0f172a);color:#fff;font-family:system-ui,sans-serif;text-align:center"><div><div style="font-size:64px;font-weight:800">Smart Health Assistant</div><div style="font-size:26px;opacity:.85;margin-top:14px">Angular · Spring Boot · Machine Learning · Claude</div></div></div>`);
    await sleep(NARR_INTRO_HOLD());
    await go('/login');
  }

  console.log('1. Admin invites a doctor; the doctor sets a password');
  await say('admin');
  await signIn(ADMIN.email, ADMIN.password); await shot('admin-doctors');
  await clickText('Add doctor'); await sleep(500);
  await type('#d-name', NEW_DOC.name); await type('#d-email', NEW_DOC.email); await type('#d-spec', NEW_DOC.specialty);
  await shot('admin-add-doctor');
  await clickText('Send invitation', 'form button[type=submit]'); await sleep(1500);
  await type('input[type=search]', NEW_DOC.name); await sleep(1000); await shot('admin-doctor-invited');
  await signOut();
  await go(`/reset-password?token=${lastEmailToken('reset-password')}`);
  await type('#password', NEW_DOC.password); await type('#confirmPassword', NEW_DOC.password); await shot('doctor-invite');
  await clickText('Save password', 'button[type=submit]'); await sleep(1500);

  console.log('2. Patient: register, verify email');
  await say('signup');
  await go('/login'); await shot('login');
  await go('/register');
  await type('#fullName', PATIENT.name); await type('#email', PATIENT.email);
  await type('#password', PATIENT.password); await type('#confirmPassword', PATIENT.password);
  await shot('register');
  await clickText('Create account', 'button[type=submit]');
  await page.waitForFunction(() => location.pathname === '/dashboard', { timeout: 15000 });
  await sleep(1500); await shot('dashboard-unverified-banner');
  await go(`/verify-email?token=${lastEmailToken('verify-email')}`); await sleep(1200); await shot('email-verified');
  await go('/dashboard'); await sleep(1000);

  console.log('3. Find a doctor (ML specialty recommender)');
  await say('recommend');
  await go('/recommend');
  await type('#symptoms', 'I have an itchy red rash on my arms that has been spreading for a week');
  await shot('recommend-form');
  await clickText('Find doctors', 'button[type=submit]');
  await waitFor(() => document.body.innerText.includes('Suggested doctors') || document.body.innerText.includes('emergency'), 'recommendations');
  await shot('recommend-result');

  console.log('4. Emergency detection (rules, not ML)');
  await say('emergency');
  await sleep(800);
  await type('#symptoms', 'I have crushing chest pain and my left arm is numb');
  await clickText('Find doctors', 'button[type=submit]');
  await waitFor(() => document.body.innerText.includes('emergency'), 'emergency warning');
  await shot('recommend-emergency');

  console.log('5. Diabetes risk check (ML with explanation)');
  await say('risk');
  await go('/risk');
  await type('#age', '52'); await type('#glucose', '168'); await type('#bmi', '33.5'); await type('#pregnancies', '2');
  await shot('risk-form');
  await clickText('Check my risk', 'button[type=submit]');
  await waitFor(() => document.body.innerText.includes('What shaped this result'), 'risk result');
  await sleep(800); await shot('risk-result');
  await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight)); await shot('risk-history');

  console.log('6. AI chat "Aria" (streaming)');
  await say('chat');
  await go('/chat'); await shot('chat-empty');
  await page.type('textarea', 'I have had a headache and feel dizzy since yesterday', { delay: VIDEO ? 45 : 0 });
  await page.keyboard.press('Enter');
  await sleep(9000); await shot('chat-reply');
  await page.type('textarea', 'I am having severe chest pain and trouble breathing', { delay: VIDEO ? 45 : 0 });
  await page.keyboard.press('Enter'); await sleep(5000); await shot('chat-emergency');

  console.log('7. Book, reschedule');
  await say('book');
  await go('/appointments/new');
  await type('input[type=search]', 'Ivy'); await sleep(600);
  await page.click('.doctor');
  await setValue('#dateTime', tomorrow(10)); await type('#reason', 'Itchy red rash on both arms for a week');
  await shot('book-form');
  await clickText('Confirm booking', 'button[type=submit]');
  await page.waitForFunction(() => location.pathname === '/appointments', { timeout: 15000 });
  await sleep(1500); await shot('appointments-list');
  await clickText('Reschedule'); await sleep(600);
  await setValue('#rs-dateTime', tomorrow(14)); await sleep(300);
  await shot('reschedule-dialog');
  await clickText('Save changes', 'form button[type=submit]'); await sleep(1500); await shot('appointments-rescheduled');

  console.log('8. Message the doctor');
  await say('message');
  await clickText('Messages'); await page.waitForSelector('textarea');
  await page.type('textarea', 'Hello Doctor, the rash is itchier at night. Should I stop using my new soap?', { delay: VIDEO ? 40 : 0 });
  await shot('messages-patient-draft');
  await page.keyboard.press('Enter'); await sleep(1500); await shot('messages-patient-sent');
  await signOut();

  console.log('9. Doctor: sees the booking, reads and replies, completes');
  await say('doctor');
  await signIn(NEW_DOC.email, NEW_DOC.password); await shot('doctor-dashboard');
  await go('/appointments'); await sleep(1200); await shot('doctor-appointments');
  await clickText('Messages'); await page.waitForSelector('textarea'); await sleep(1200); await shot('messages-doctor-read');
  await page.type('textarea', 'Thanks for letting me know. Yes, please stop the new soap and use a fragrance-free moisturiser. I will check the rash at your visit.', { delay: VIDEO ? 35 : 0 });
  await page.keyboard.press('Enter'); await sleep(1500); await shot('messages-doctor-replied');
  await clickText('Back'); await page.waitForFunction(() => location.pathname === '/appointments'); await sleep(800);
  await clickExact('Complete'); await sleep(1200);
  await clickExact('Completed'); await sleep(1500); await shot('doctor-completed');
  await signOut();

  console.log('10. Patient sees the reply');
  await say('reply');
  await signIn(PATIENT.email, PATIENT.password);
  await go('/appointments'); await sleep(1200);
  await clickText('Messages'); await page.waitForSelector('textarea'); await sleep(1500); await shot('messages-patient-reply');
  await signOut();

  console.log('11. Password reset');
  await say('reset');
  await go('/forgot-password'); await type('#email', PATIENT.email); await shot('forgot-password');
  await clickText('Send reset link', 'button[type=submit]'); await sleep(1500); await shot('forgot-password-sent');
  await go(`/reset-password?token=${lastEmailToken('reset-password')}`);
  PATIENT.password = 'Patient-NewPass-456';
  await type('#password', PATIENT.password); await type('#confirmPassword', PATIENT.password); await shot('reset-password');
  await clickText('Save password', 'button[type=submit]'); await sleep(1500); await shot('reset-done');

  console.log('12. Login rate limiting');
  await say('lock');
  await go('/login');
  for (let i = 0; i < 6; i++) {
    await type('#email', PATIENT.email); await type('#password', `wrong-guess-${i}`);
    await clickText('Sign in', 'button[type=submit]'); await sleep(900);
  }
  await shot('login-locked');

  if (VIDEO) {
    await say('tech');
    const items = [['Frontend', 'Angular 20 · signals · lazy routes · interceptors'], ['API', 'Spring Boot 3.5 · Java 21 · Spring Security · JWT'], ['ML service', 'Python · FastAPI · scikit-learn'], ['AI chat', 'Claude via the Anthropic Java SDK · SSE streaming'], ['Security', 'Rotating refresh cookies · AES-256-GCM · rate limiting · HSTS'], ['Quality', '250+ JUnit tests · Karma · pytest · OpenAPI docs']];
    await card(`<div style="height:100vh;display:grid;place-items:center;background:#0f172a;color:#fff;font-family:system-ui,sans-serif"><div style="width:900px"><div style="font-size:44px;font-weight:800;margin-bottom:28px">Tech stack</div>${items.map(([a, b]) => `<div style="display:flex;gap:24px;padding:14px 0;border-top:1px solid #334155;font-size:24px"><span style="width:200px;color:#5eead4;font-weight:700">${a}</span><span>${b}</span></div>`).join('')}</div></div>`);
    await say('outro');
    await card(`<div style="height:100vh;display:grid;place-items:center;background:linear-gradient(135deg,#0f766e,#0f172a);color:#fff;font-family:system-ui,sans-serif;text-align:center"><div><div style="font-size:72px;font-weight:800">Thank you for watching!</div><div style="font-size:34px;margin-top:22px">Follow the channel for more projects like this</div><div style="font-size:24px;opacity:.85;margin-top:30px">github.com/VaibhavBansal26/smart-health-assistant</div></div></div>`);
    // The screencast only emits frames while pixels change, so keep a subtle change going until the line ends.
    let flip = 0;
    while (Date.now() < speakingUntil + 1200) { await page.evaluate((f) => { document.body.firstElementChild.style.opacity = f ? '0.97' : '1'; }, flip++ % 2); await sleep(400); }
    await rec.stop();
    writeFileSync(join(OUT, 'narration-timeline.json'), JSON.stringify({ totalMs: Date.now() - t0, lines: timeline }, null, 2));
  }
  console.log(`Done. ${n} screenshots (patient ${PATIENT.email})`);
} catch (e) {
  console.error('FAILED:', e.message);
  if (VIDEO) await rec.stop().catch(() => {});
  await page.screenshot({ path: join(OUT, 'zz-failure.png') });
  process.exitCode = 1;
} finally {
  await browser.close();
}
function NARR_INTRO_HOLD() { return Math.max(0, speakingUntil - Date.now()) + 500; }
