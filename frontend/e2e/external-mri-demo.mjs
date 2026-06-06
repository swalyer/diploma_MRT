// Uploads an external real liver MRI (LiverHccSeg / TCGA-LIHC, NOT ATLAS) as an
// MRI case and processes it with the real model. Arg: absolute NIfTI path + label.
import { chromium } from '@playwright/test'
import fs from 'node:fs'

const API = 'http://localhost:8080'
const UI = 'http://localhost:5173'
const IMG = process.argv[2]
const LABEL = process.argv[3] || 'EXT-LIVERHCCSEG'

const token = await (await fetch(`${API}/api/auth/login`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email: 'doctor@demo.local', password: 'Admin123!' }),
})).json().then((r) => r.token)
const H = (e = {}) => ({ Authorization: `Bearer ${token}`, ...e })

const c = await (await fetch(`${API}/api/cases`, { method: 'POST', headers: H({ 'Content-Type': 'application/json' }), body: JSON.stringify({ patientPseudoId: `${LABEL}-${Date.now()}`, modality: 'MRI' }) })).json()
console.log('case', c.id)
const fd = new FormData()
fd.append('file', new Blob([fs.readFileSync(IMG)]), 'external_pv.nii.gz')
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
const findings = await (await fetch(`${API}/api/cases/${c.id}/findings`, { headers: H() })).json()
console.log('findings', findings.map((f) => `vol=${Math.round(f.volumeMm3)}mm3 conf=${f.confidence?.toFixed?.(2)}`).join(' | '))

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1440, height: 1100 } })
await page.addInitScript((t) => localStorage.setItem('mrt.auth.token', t), token)
await page.addInitScript(() => localStorage.setItem('mrt.theme.mode', 'dark'))
await page.goto(`${UI}/cases/${c.id}`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1500)
await page.screenshot({ path: 'external-mri.png', fullPage: true })
await browser.close()
console.log('saved external-mri.png (case', c.id, ')')
