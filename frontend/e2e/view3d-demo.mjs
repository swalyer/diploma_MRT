import { chromium } from '@playwright/test'
const UI = 'http://localhost:5173'
const caseId = process.argv[2] || '33'
const token = await (await fetch('http://localhost:8080/api/auth/login', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email: 'doctor@demo.local', password: 'Admin123!' }),
})).json().then((r) => r.token)
const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1280, height: 900 } })
await page.addInitScript((t) => localStorage.setItem('mrt.auth.token', t), token)
await page.addInitScript(() => localStorage.setItem('mrt.theme.mode', 'dark'))
await page.goto(`${UI}/cases/${caseId}`, { waitUntil: 'networkidle' })
await page.getByRole('tab', { name: '3D Viewer' }).click()
await page.waitForTimeout(4000) // let GLB load + render
await page.screenshot({ path: `view3d-${caseId}.png` })
await browser.close()
console.log(`saved view3d-${caseId}.png`)
