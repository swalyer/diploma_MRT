// Re-capture dashboard + admin as viewport-only (full-page was too tall for embedding).
import { chromium } from '@playwright/test'
const API = 'http://localhost:8080', UI = 'http://localhost:5173'
const OUT = 'C:\\Users\\pro10\\Desktop\\diploma_MRT-codex-full-readiness-bulk-pr\\docs\\diploma\\figures\\screens'
const tok = async (e) => (await (await fetch(`${API}/api/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email: e, password: 'Admin123!' }) })).json()).token
const b = await chromium.launch()
for (const [email, path, png] of [['doctor@demo.local', '/cases', '02_dashboard.png'], ['admin@demo.local', '/admin', '08_admin.png']]) {
  const t = await tok(email)
  const c = await b.newContext({ viewport: { width: 1440, height: 1000 } })
  await c.addInitScript((x) => localStorage.setItem('mrt.auth.token', x), t)
  const p = await c.newPage()
  await p.goto(`${UI}${path}`, { waitUntil: 'networkidle' })
  await p.waitForTimeout(1600)
  await p.screenshot({ path: `${OUT}/${png}` })
  await c.close()
}
await b.close()
console.log('re-captured dashboard + admin (viewport)')
