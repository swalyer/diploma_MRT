// Drives one REAL MRI case through the live stack (ml-service in real mode with
// the trained ATLAS nnU-Net), then screenshots the result showing the honest
// "real model" chip. Requires the stack up with ML_NNUNET_MRI_MODEL_DIR set.
import { chromium } from '@playwright/test'
import fs from 'node:fs'

const API = 'http://localhost:8080'
const UI = 'http://localhost:5173'
const CASE = process.argv[2] || 'im36'
const IMG = `C:\\Users\\pro10\\nnunet\\raw\\Dataset501_AtlasMRILesion\\imagesTr\\${CASE}_0000.nii.gz`

const token = await (await fetch(`${API}/api/auth/login`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email: 'doctor@demo.local', password: 'Admin123!' }),
})).json().then((r) => r.token)
const H = (e = {}) => ({ Authorization: `Bearer ${token}`, ...e })

const c = await (await fetch(`${API}/api/cases`, { method: 'POST', headers: H({ 'Content-Type': 'application/json' }), body: JSON.stringify({ patientPseudoId: `REAL-MRI-${Date.now()}`, modality: 'MRI' }) })).json()
console.log('case', c.id)
const fd = new FormData()
fd.append('file', new Blob([fs.readFileSync(IMG)]), 'im36_0000.nii.gz')
await fetch(`${API}/api/cases/${c.id}/upload`, { method: 'POST', headers: H(), body: fd })
await fetch(`${API}/api/cases/${c.id}/process`, { method: 'POST', headers: H() })

let status = 'UNKNOWN', metrics = null
const deadline = Date.now() + 360000
while (Date.now() < deadline) {
  const s = await (await fetch(`${API}/api/cases/${c.id}/status`, { headers: H() })).json()
  status = s.status; metrics = s.metrics
  if (status === 'COMPLETED' || status === 'FAILED') break
  await new Promise((r) => setTimeout(r, 4000))
}
console.log('final status', status)
console.log('metrics', JSON.stringify(metrics))

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1440, height: 1100 } })
await page.addInitScript((t) => localStorage.setItem('mrt.auth.token', t), token)
await page.addInitScript(() => localStorage.setItem('mrt.theme.mode', 'dark'))
await page.goto(`${UI}/cases/${c.id}`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1500)
await page.screenshot({ path: 'real-mri-overview.png', fullPage: true })
await browser.close()
console.log('saved real-mri-overview.png')
