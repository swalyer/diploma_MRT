// Seeds two completed CT studies for the same patient, then screenshots the
// Longitudinal comparison tab on the follow-up. Requires the live stack.
import { chromium } from '@playwright/test'
import fs from 'node:fs'

const API = 'http://localhost:8080'
const UI = 'http://localhost:5173'
const PSEUDO = `LONGI-${Date.now()}`

async function login(email) {
  const r = await fetch(`${API}/api/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, password: 'Admin123!' }) })
  return (await r.json()).token
}
const token = await login('doctor@demo.local')
const H = (e = {}) => ({ Authorization: `Bearer ${token}`, ...e })
const buf = fs.readFileSync('../../example4d.nii.gz')

async function makeCase() {
  const c = await (await fetch(`${API}/api/cases`, { method: 'POST', headers: H({ 'Content-Type': 'application/json' }), body: JSON.stringify({ patientPseudoId: PSEUDO, modality: 'CT' }) })).json()
  const fd = new FormData()
  fd.append('file', new Blob([buf]), 'example4d.nii.gz')
  await fetch(`${API}/api/cases/${c.id}/upload`, { method: 'POST', headers: H(), body: fd })
  await fetch(`${API}/api/cases/${c.id}/process`, { method: 'POST', headers: H() })
  // poll completed
  for (let i = 0; i < 45; i++) {
    const s = await (await fetch(`${API}/api/cases/${c.id}/status`, { headers: H() })).json()
    if (s.status === 'COMPLETED' || s.status === 'FAILED') return { id: c.id, status: s.status }
    await new Promise((r) => setTimeout(r, 2000))
  }
  return { id: c.id, status: 'TIMEOUT' }
}

const a = await makeCase()
const b = await makeCase()
console.log('baseline', a, 'followup', b)

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
await page.addInitScript((t) => localStorage.setItem('mrt.auth.token', t), token)
await page.addInitScript(() => localStorage.setItem('mrt.theme.mode', 'dark'))
await page.goto(`${UI}/cases/${b.id}`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1200)
await page.getByRole('tab', { name: 'Longitudinal' }).click()
await page.waitForTimeout(1000)
await page.getByTestId('comparison-baseline-select').click()
await page.waitForTimeout(300)
await page.getByRole('option', { name: new RegExp(`Case #${a.id} `) }).click()
await page.waitForTimeout(1200)
await page.screenshot({ path: 'longitudinal-demo.png', fullPage: true })
await browser.close()
console.log('saved longitudinal-demo.png')
