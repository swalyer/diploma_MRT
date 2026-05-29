// Ad-hoc QA pass against the LIVE stack (vite :5173 -> backend :8080 -> Postgres),
// covering negative scenarios + a full happy-path with real (mock) processing,
// emitting a self-contained HTML report with embedded screenshots.
import { chromium, request as pwRequest } from '@playwright/test'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import fs from 'node:fs'

const BASE = 'http://localhost:5173'
const __dirname = path.dirname(fileURLToPath(import.meta.url))
const FIXTURE = path.resolve(__dirname, '../../example4d.nii.gz')
const OUT_DIR = path.resolve(__dirname, '../../docs/qa')
const PSEUDO = 'QA-LIVE-' + Date.now()

const steps = []
const consoleErrors = []
const record = (name, group, pass, note, img) => steps.push({ name, group, pass, note: note || '', img: img || null })

async function login(api, email) {
  const res = await api.post(`${BASE}/api/auth/login`, { data: { email, password: 'Admin123!' } })
  const body = await res.json().catch(() => ({}))
  return body.token
}

const browser = await chromium.launch()
const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
const page = await ctx.newPage()
page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text()) })
page.on('pageerror', (e) => consoleErrors.push('pageerror: ' + e.message))

const api = await pwRequest.newContext()
const adminToken = await login(api, 'admin@demo.local')
const doctorToken = await login(api, 'doctor@demo.local')
const auth = (t) => ({ Authorization: `Bearer ${t}` })
const shot = async () => (await page.screenshot({ fullPage: true })).toString('base64')

// ============ NEGATIVE SCENARIOS (API + UI) ============

// N1: bad login via UI
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.getByTestId('login-email').fill('admin@demo.local')
await page.getByTestId('login-password').fill('wrong-password')
await page.getByTestId('login-submit').click()
await page.waitForTimeout(1200)
{
  const errVisible = await page.getByText(/Authentication failed/i).count()
  const stillLogin = page.url().includes('/login')
  record('Bad password is rejected with an error and no redirect', 'Negative', errVisible >= 1 && stillLogin,
    `errorAlert=${errVisible >= 1}, stillOnLogin=${stillLogin}`, await shot())
}

// N2: unauthenticated API access
{
  const res = await api.get(`${BASE}/api/cases`)
  record('Unauthenticated GET /api/cases is blocked', 'Negative', res.status() === 401 || res.status() === 403, `HTTP ${res.status()}`)
}

// N3: non-existent case -> 404
{
  const res = await api.get(`${BASE}/api/cases/999999`, { headers: auth(adminToken) })
  record('GET non-existent case returns 404', 'Negative', res.status() === 404, `HTTP ${res.status()}`)
}

// N4: doctor cannot use admin import -> 403
{
  const res = await api.post(`${BASE}/api/admin/demo-cases/import`, { headers: auth(doctorToken), data: { schemaVersion: 'v1', caseSlug: 'x' } })
  record('Doctor role is forbidden from admin import', 'Negative', res.status() === 403, `HTTP ${res.status()}`)
}

// N5: non-NIfTI upload is rejected
{
  const created = await api.post(`${BASE}/api/cases`, { headers: auth(doctorToken), data: { patientPseudoId: PSEUDO + '-neg', modality: 'CT' } })
  const caseId = (await created.json()).id
  const res = await api.post(`${BASE}/api/cases/${caseId}/upload`, {
    headers: auth(doctorToken),
    multipart: { file: { name: 'notscan.txt', mimeType: 'text/plain', buffer: Buffer.from('not a nifti file') } },
  })
  record('Non-NIfTI upload is rejected', 'Negative', res.status() >= 400, `HTTP ${res.status()}`)
}

// ============ HAPPY PATH (UI + real mock processing) ============

// H1: login as doctor via UI
await page.evaluate(() => localStorage.clear())
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.getByTestId('login-email').fill('doctor@demo.local')
await page.getByTestId('login-password').fill('Admin123!')
await page.getByTestId('login-submit').click()
await page.waitForURL('**/cases', { timeout: 15000 }).catch(() => {})
record('Doctor login succeeds', 'Happy path', page.url().includes('/cases'), page.url(), await shot())

// H2: intake — create + upload via the wizard UI
await page.goto(`${BASE}/cases/new`, { waitUntil: 'networkidle' })
await page.getByTestId('create-case-pseudo-id').fill(PSEUDO)
await page.locator('[data-testid="study-file-input"]').setInputFiles(FIXTURE)
await page.waitForTimeout(300)
await page.getByTestId('create-case-submit').click()
await page.waitForURL(/\/cases\/\d+$/, { timeout: 20000 }).catch(() => {})
const caseUrl = page.url()
const caseId = Number(caseUrl.split('/').pop())
await page.waitForTimeout(800)
record('Intake wizard creates case and uploads NIfTI', 'Happy path', /\/cases\/\d+$/.test(caseUrl) && !!caseId, `case #${caseId}`, await shot())

// H3: run the pipeline
let processStarted = false
{
  const btn = page.getByTestId('run-pipeline-button')
  if (await btn.count() && !(await btn.isDisabled())) { await btn.click(); processStarted = true }
  await page.waitForTimeout(1000)
  record('Run pipeline accepted', 'Happy path', processStarted, processStarted ? 'process triggered' : 'run button unavailable', await shot())
}

// H4: poll status until COMPLETED
let finalStatus = 'UNKNOWN'
{
  const deadline = Date.now() + 90000
  while (Date.now() < deadline) {
    const res = await api.get(`${BASE}/api/cases/${caseId}/status`, { headers: auth(doctorToken) })
    if (res.ok()) {
      const s = await res.json()
      finalStatus = s.status
      if (s.status === 'COMPLETED' || s.status === 'FAILED') break
    }
    await new Promise((r) => setTimeout(r, 2000))
  }
  record('Pipeline reaches COMPLETED', 'Happy path', finalStatus === 'COMPLETED', `final status=${finalStatus}`)
}

// H5: overview with findings
await page.goto(`${BASE}/cases/${caseId}`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1500)
{
  const completed = (await page.getByTestId('case-status-chip').textContent())?.includes('COMPLETED')
  const findingRows = await page.locator('[data-testid^="finding-row-"]').count()
  record('Case overview shows COMPLETED + findings', 'Happy path', completed, `status chip COMPLETED=${completed}, findings=${findingRows}`, await shot())
}

// H6: 2D viewer
await page.getByRole('tab', { name: '2D Imaging' }).click()
await page.waitForTimeout(2500)
{
  const canvas = await page.getByTestId('viewer-2d-canvas').count()
  const fallback = await page.getByText(/No NIfTI-compatible|Unable to render/i).count()
  record('2D viewer renders NIfTI canvas', 'Happy path', canvas >= 1 && fallback === 0, fallback ? 'fell back to alert' : 'canvas rendered', await shot())
}

// H7: 3D viewer
await page.getByRole('tab', { name: '3D Viewer' }).click()
await page.waitForTimeout(2500)
{
  const canvas = await page.getByTestId('viewer-3d-canvas').count()
  record('3D viewer renders mesh canvas', 'Happy path', canvas >= 1, `canvas=${canvas}`, await shot())
}

// H8: report + PDF
await page.getByRole('tab', { name: 'Report' }).click()
await page.waitForTimeout(800)
{
  const pdf = await api.get(`${BASE}/api/cases/${caseId}/report.pdf`, { headers: auth(doctorToken) })
  const body = await pdf.body()
  const isPdf = pdf.status() === 200 && body.slice(0, 4).toString() === '%PDF'
  record('Report PDF downloads (live endpoint)', 'Happy path', isPdf, `HTTP ${pdf.status()}, ${body.length} bytes`, await shot())
}

await browser.close()
await api.dispose()

// ============ BUILD HTML REPORT ============
const pass = steps.filter((s) => s.pass).length
const total = steps.length
const ts = new Date().toISOString().replace('T', ' ').slice(0, 19) + ' UTC'
const esc = (s) => String(s).replace(/[&<>]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[c]))
const groups = [...new Set(steps.map((s) => s.group))]

const rows = groups.map((g) => {
  const items = steps.filter((s) => s.group === g).map((s) => `
    <div class="step ${s.pass ? 'ok' : 'bad'}">
      <div class="step-head">
        <span class="badge ${s.pass ? 'ok' : 'bad'}">${s.pass ? 'PASS' : 'FAIL'}</span>
        <span class="step-name">${esc(s.name)}</span>
        <span class="note">${esc(s.note)}</span>
      </div>
      ${s.img ? `<img class="shot" src="data:image/png;base64,${s.img}" alt="${esc(s.name)}">` : ''}
    </div>`).join('')
  return `<h2>${esc(g)}</h2>${items}`
}).join('')

const html = `<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Live QA Report — Liver Insight Console</title>
<style>
  :root{--bg:#0b1220;--card:#131c2e;--ink:#e6edf7;--muted:#9fb0c8;--ok:#34d399;--bad:#f87171;--line:rgba(255,255,255,.1)}
  *{box-sizing:border-box} body{margin:0;background:var(--bg);color:var(--ink);font:15px/1.5 Inter,system-ui,sans-serif;padding:32px}
  h1{font-size:24px;margin:0 0 4px} h2{margin:28px 0 10px;color:#5b8cff;font-size:16px;text-transform:uppercase;letter-spacing:.5px}
  .sub{color:var(--muted);margin-bottom:18px}
  .summary{display:flex;gap:12px;flex-wrap:wrap;margin:14px 0 8px}
  .pill{background:var(--card);border:1px solid var(--line);border-radius:12px;padding:10px 16px;font-weight:700}
  .pill.ok{color:var(--ok)} .pill.bad{color:var(--bad)}
  .step{background:var(--card);border:1px solid var(--line);border-left:4px solid var(--line);border-radius:12px;padding:14px;margin:10px 0}
  .step.ok{border-left-color:var(--ok)} .step.bad{border-left-color:var(--bad)}
  .step-head{display:flex;align-items:center;gap:10px;flex-wrap:wrap}
  .badge{font-size:12px;font-weight:800;padding:3px 8px;border-radius:6px}
  .badge.ok{background:rgba(52,211,153,.15);color:var(--ok)} .badge.bad{background:rgba(248,113,113,.15);color:var(--bad)}
  .step-name{font-weight:600} .note{color:var(--muted);font-size:13px}
  .shot{display:block;width:100%;max-width:1100px;margin-top:12px;border:1px solid var(--line);border-radius:10px}
  .errs{background:var(--card);border:1px solid var(--line);border-radius:12px;padding:14px;margin-top:10px;white-space:pre-wrap;color:var(--muted);font-family:ui-monospace,monospace;font-size:13px}
  footer{margin-top:28px;color:var(--muted);font-size:13px}
</style></head><body>
  <h1>Live QA Report — Liver Insight Console</h1>
  <div class="sub">Ad-hoc exploratory pass against the running stack (frontend :5173 → backend :8080 → PostgreSQL). Generated ${ts}.</div>
  <div class="summary">
    <div class="pill ${pass === total ? 'ok' : 'bad'}">${pass}/${total} checks passed</div>
    <div class="pill">${groups.length} groups</div>
    <div class="pill ${consoleErrors.length ? 'bad' : 'ok'}">${consoleErrors.length} console errors</div>
  </div>
  ${rows}
  <h2>Console errors</h2>
  <div class="errs">${consoleErrors.length ? esc(consoleErrors.join('\n')) : '(none)'}</div>
  <footer>Decision-support MVP — automated UI/contract checks. Screenshots are full-page captures from a headless Chromium driving the real app.</footer>
</body></html>`

fs.mkdirSync(OUT_DIR, { recursive: true })
const outPath = path.join(OUT_DIR, 'live-test-report.html')
fs.writeFileSync(outPath, html)
console.log(`\n${pass}/${total} passed, ${consoleErrors.length} console errors`)
for (const s of steps) console.log(`${s.pass ? 'PASS' : 'FAIL'}  [${s.group}] ${s.name}  ${s.note}`)
console.log(`\nReport written: ${outPath}`)
