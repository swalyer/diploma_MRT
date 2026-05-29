// One-off visual check: capture the login page in dark + light themes.
// Run while `npm run dev` serves the app on :5173.
import { chromium } from '@playwright/test'

const BASE = process.env.PREVIEW_BASE_URL || 'http://localhost:5173'
const out = 'theme-preview'

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })

await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForTimeout(500)

// Default mode (dark per store fallback) — capture, then toggle to the other.
await page.screenshot({ path: `${out}-login-default.png` })

await page.getByLabel('toggle color theme').click()
await page.waitForTimeout(400)
await page.screenshot({ path: `${out}-login-toggled.png` })

await browser.close()
console.log('Saved', `${out}-login-default.png`, 'and', `${out}-login-toggled.png`)
