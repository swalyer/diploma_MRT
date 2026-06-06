// Captures demonstration screenshots of the running stack for the thesis (chapter 6).
import { chromium } from '@playwright/test'
import fs from 'node:fs'

const API = 'http://localhost:8080'
const UI = 'http://localhost:5173'
const CASE = process.argv[2] || '34'
const OUT = 'C:\\Users\\pro10\\Desktop\\diploma_MRT-codex-full-readiness-bulk-pr\\docs\\diploma\\figures\\screens'
fs.mkdirSync(OUT, { recursive: true })

async function token(email) {
  return (await (await fetch(`${API}/api/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password: 'Admin123!' }),
  })).json()).token
}

const browser = await chromium.launch()

// 1. login page (no auth)
{
  const p = await browser.newPage({ viewport: { width: 1440, height: 900 } })
  await p.goto(`${UI}/login`, { waitUntil: 'networkidle' }).catch(() => {})
  await p.waitForTimeout(1200)
  await p.screenshot({ path: `${OUT}/01_login.png` })
  await p.close()
}

const docTok = await token('doctor@demo.local')
const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
await ctx.addInitScript((t) => localStorage.setItem('mrt.auth.token', t), docTok)
const page = await ctx.newPage()

// 2. dashboard / case list
await page.goto(`${UI}/cases`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1500)
await page.screenshot({ path: `${OUT}/02_dashboard.png`, fullPage: true })

// 3. case overview (real model)
await page.goto(`${UI}/cases/${CASE}`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1800)
await page.screenshot({ path: `${OUT}/03_overview.png`, fullPage: true })

// 4. 2D imaging
await page.getByRole('tab', { name: '2D Imaging' }).click()
await page.waitForTimeout(2500)
await page.screenshot({ path: `${OUT}/04_2d.png` })

// 5. report
await page.getByRole('tab', { name: 'Report' }).click()
await page.waitForTimeout(1500)
await page.screenshot({ path: `${OUT}/05_report.png`, fullPage: true })

// 6. 3D viewer
await page.getByRole('tab', { name: '3D Viewer' }).click()
await page.waitForTimeout(4500)
await page.screenshot({ path: `${OUT}/06_3d.png` })

// 7. audit / admin tab (real audit trail)
await page.getByRole('tab', { name: 'Audit / Admin' }).click()
await page.waitForTimeout(1200)
await page.screenshot({ path: `${OUT}/07_audit.png`, fullPage: true })

await ctx.close()

// 8. admin console (admin login)
const admTok = await token('admin@demo.local')
const actx = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
await actx.addInitScript((t) => localStorage.setItem('mrt.auth.token', t), admTok)
const ap = await actx.newPage()
await ap.goto(`${UI}/admin`, { waitUntil: 'networkidle' })
await ap.waitForTimeout(1800)
await ap.screenshot({ path: `${OUT}/08_admin.png`, fullPage: true })
await actx.close()

await browser.close()
console.log('screens saved to', OUT)
