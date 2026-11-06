// Walks through every feature of the running app and saves screenshots to docs/demo/.
// VIDEO=1 records docs/demo/demo.mp4 (captions, slower typing, needs ffmpeg) instead of screenshots.
// Needs: ML service, backend (MAIL_MODE=log, SEED_DEMO_DATA=true, ADMIN_* set) and frontend running.
// Config via env: CHROME_BIN, BACKEND_LOG (file the backend logs to), APP_URL, API_URL,
//                 ADMIN_EMAIL, ADMIN_PASSWORD, DEMO_DOCTOR_PASSWORD.
import puppeteer from 'puppeteer-core';
import { readFileSync, mkdirSync } from 'node:fs';
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
const DOCTOR_FOR_BOOKING = 'Cardio';

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
async function caption(text) {
  if (!VIDEO) return;
  await page.evaluate((t) => { window.name = 'cap:' + t; document.getElementById('demo-cap')?.remove(); const e = document.createElement('div'); e.id = 'demo-cap'; e.textContent = t; e.style.cssText = 'position:fixed;left:0;right:0;bottom:0;z-index:99999;padding:14px 24px;font:600 20px system-ui,sans-serif;color:#fff;background:rgba(15,23,42,.88);text-align:center;pointer-events:none'; document.body.appendChild(e); }, text);
}

async function shot(name) {
  if (VIDEO) { await sleep(2200); return; }
  await sleep(500);
  const file = `${String(++n).padStart(2, '0')}-${name}.png`;
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
}
try {
  console.log('1. Accounts: register, verify email');
  await caption('Patients sign up and verify their email');
  await go('/login'); await shot('login');
  await go('/register');
  await type('#fullName', PATIENT.name); await type('#email', PATIENT.email);
  await type('#password', PATIENT.password); await type('#confirmPassword', PATIENT.password);
  await shot('register');
  await clickText('Create account', 'button[type=submit]');
  await page.waitForFunction(() => location.pathname === '/dashboard', { timeout: 15000 });
  await sleep(1500); await shot('dashboard-unverified-banner');
  await sleep(1000);
  await go(`/verify-email?token=${lastEmailToken('verify-email')}`); await sleep(1200); await shot('email-verified');
  await go('/dashboard'); await sleep(1000);

  console.log('2. Find a doctor (ML specialty recommender)');
  await caption('ML recommender: symptoms in, matching specialty and doctors out');
  await go('/recommend');
  await type('#symptoms', "I've had a dry cough for two weeks and I get wheezy at night");
  await shot('recommend-form');
  await clickText('Find doctors', 'button[type=submit]');
  await waitFor(() => document.body.innerText.includes('Suggested doctors') || document.body.innerText.includes('emergency'), 'recommendations');
  await shot('recommend-result');

  console.log('3. Emergency detection (rules, not ML)');
  await caption('Emergency wording is caught by rules, not ML');
  await sleep(800);
  await type('#symptoms', 'I have crushing chest pain and my left arm is numb');
  await clickText('Find doctors', 'button[type=submit]');
  await waitFor(() => document.body.innerText.includes('emergency'), 'emergency warning');
  await shot('recommend-emergency');

  console.log('4. Diabetes risk check (ML with explanation)');
  await caption('Diabetes risk check: score plus what pushed it up or down');
  await go('/risk');
  await type('#age', '52'); await type('#glucose', '168'); await type('#bmi', '33.5'); await type('#pregnancies', '2');
  await shot('risk-form');
  await clickText('Check my risk', 'button[type=submit]');
  await waitFor(() => document.body.innerText.includes('What shaped this result'), 'risk result');
  await sleep(800); await shot('risk-result');
  await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight)); await shot('risk-history');

  console.log('5. AI chat "Aria" (streaming)');
  await caption('Aria chat: streaming replies, emergency override');
  await go('/chat'); await shot('chat-empty');
  await page.type('textarea', 'I have had a headache and feel dizzy since yesterday');
  await page.keyboard.press('Enter');
  await sleep(9000); await shot('chat-reply');
  await page.type('textarea', 'I am having severe chest pain and trouble breathing');
  await page.keyboard.press('Enter'); await sleep(5000); await shot('chat-emergency');

  console.log('6. Book, list, reschedule');
  await caption('Book, list and reschedule appointments');
  await go('/appointments/new');
  await type('input[type=search]', DOCTOR_FOR_BOOKING); await sleep(500);
  await page.click('.doctor');
  await setValue('#dateTime', tomorrow(10)); await type('#reason', 'Chest tightness when climbing stairs');
  await shot('book-form');
  await clickText('Confirm booking', 'button[type=submit]');
  await page.waitForFunction(() => location.pathname === '/appointments', { timeout: 15000 });
  await sleep(1500); await shot('appointments-list');
  await clickText('Reschedule'); await sleep(600);
  await setValue('#rs-dateTime', tomorrow(14)); await sleep(300);
  await shot('reschedule-dialog');
  await clickText('Save changes', 'form button[type=submit]'); await sleep(1500); await shot('appointments-rescheduled');
  await go('/dashboard'); await sleep(1000); await shot('dashboard-summary');

  console.log('7. Password reset');
  await caption('Password reset by single-use emailed link');
  await signOut();
  await go('/forgot-password'); await type('#email', PATIENT.email); await shot('forgot-password');
  await clickText('Send reset link', 'button[type=submit]'); await sleep(1500); await shot('forgot-password-sent');
  await go(`/reset-password?token=${lastEmailToken('reset-password')}`);
  PATIENT.password = 'Patient-NewPass-456';
  await type('#password', PATIENT.password); await type('#confirmPassword', PATIENT.password); await shot('reset-password');
  await clickText('Save password', 'button[type=submit]'); await sleep(1500); await shot('reset-done');

  console.log('8. Login rate limiting');
  await caption('Brute force is locked out after repeated wrong passwords');
  await go('/login');
  for (let i = 0; i < 6; i++) {
    await type('#email', PATIENT.email); await type('#password', `wrong-guess-${i}`);
    await clickText('Sign in', 'button[type=submit]'); await sleep(900);
  }
  await shot('login-locked');

  console.log('9. Admin: create a doctor by invite');
  await caption('Admin invites a new doctor by email');
  await signIn(ADMIN.email, ADMIN.password); await shot('admin-doctors');
  await clickText('Add doctor'); await sleep(500);
  await type('#d-name', NEW_DOC.name); await type('#d-email', NEW_DOC.email); await type('#d-spec', NEW_DOC.specialty);
  await shot('admin-add-doctor');
  await clickText('Send invitation', 'form button[type=submit]'); await sleep(1500);
  await type('input[type=search]', NEW_DOC.name); await sleep(1000); await shot('admin-doctor-invited');
  await signOut();

  console.log('10. Doctor accepts invite and works their schedule');
  await caption('The doctor sets a password and signs in');
  await go(`/reset-password?token=${lastEmailToken('reset-password')}`);
  await type('#password', NEW_DOC.password); await type('#confirmPassword', NEW_DOC.password); await shot('doctor-invite');
  await clickText('Save password', 'button[type=submit]'); await sleep(1500);
  await signIn(NEW_DOC.email, NEW_DOC.password); await shot('doctor-dashboard');
  await signOut();
  if (VIDEO) { await sleep(1500); await rec.stop(); }
  console.log(`Done. ${n} screenshots in docs/demo/ (patient ${PATIENT.email})`);
} catch (e) {
  console.error('FAILED:', e.message);
  if (VIDEO) await rec.stop().catch(() => {});
  await page.screenshot({ path: join(OUT, 'zz-failure.png') });
  process.exitCode = 1;
} finally {
  await browser.close();
}
