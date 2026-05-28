import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { expect, test, type Page } from '@playwright/test'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

async function importSeededManifestAndOpen(page: Page, slug: string, category: string) {
  const manifestPath = path.resolve(__dirname, `../../../demo-data/manifests/${slug}.json`)
  const manifestText = fs.readFileSync(manifestPath, 'utf-8')

  await page.goto('/login')
  await page.getByTestId('login-email').fill('admin@demo.local')
  await page.getByTestId('login-password').fill('Admin123!')
  await page.getByTestId('login-submit').click()

  await expect(page).toHaveURL(/\/cases$/)
  await page.getByRole('button', { name: 'Admin' }).click()
  await expect(page).toHaveURL(/\/admin$/)

  const readyDemoRow = page.getByRole('listitem').filter({ hasText: new RegExp(`${slug} · MRI · ${category}`, 'i') })
  if (await readyDemoRow.count()) {
    await readyDemoRow.getByRole('link', { name: 'Open' }).click()
    await expect(page).toHaveURL(/\/cases\/\d+$/)
    return
  }

  await page.getByLabel('Demo manifest JSON').fill(manifestText)
  await page.getByRole('button', { name: 'Import demo manifest' }).click()

  const importOpenLink = page.getByRole('link', { name: /Open case #/ })
  await expect.poll(async () => await importOpenLink.count(), { timeout: 15_000 }).toBeGreaterThan(0)
  await importOpenLink.click()
  await expect(page).toHaveURL(/\/cases\/\d+$/)
}

test('seeded MRI lesion case is honest-ready across report, 2D, and 3D', async ({ page }) => {
  await importSeededManifestAndOpen(page, 'mri-single-lesion-001', 'SINGLE_LESION')

  await expect(page.getByText(/Seeded demo · SINGLE_LESION/)).toBeVisible()
  await expect(page.getByText('MRI honest-ready · heuristic-supported')).toBeVisible()
  await expect(page.getByText('Result: seeded import')).toBeVisible()
  await expect(page.getByTestId('run-pipeline-button')).toBeDisabled()

  await page.getByRole('tab', { name: 'Report' }).click()
  await expect(page.getByTestId('report-content')).toContainText(/MRI suspicious-zone output remains heuristic-supported/i)
  await expect(page.getByText(/Heuristic suspicious-zone component #1/i).first()).toBeVisible()

  await page.getByRole('tab', { name: '2D Imaging' }).click()
  await expect(page.getByTestId('viewer-2d-canvas')).toBeVisible()

  await page.getByRole('tab', { name: '3D Viewer' }).click()
  await expect(page.getByTestId('viewer-3d-canvas')).toBeVisible()
  await expect(page.getByText('Suspicious zones')).toBeVisible()
  await page.getByRole('button', { name: 'Inspect' }).click()
  await expect(page.getByText('Selected Suspicious Zone')).toBeVisible()
  await expect(page.getByText(/Support: heuristic-supported/i)).toBeVisible()
})

test('seeded MRI normal case explains absent suspicious-zone mesh honestly', async ({ page }) => {
  await importSeededManifestAndOpen(page, 'mri-normal-001', 'NORMAL')

  await expect(page.getByText(/Seeded demo · NORMAL/)).toBeVisible()
  await expect(page.getByText('MRI honest-ready · heuristic-supported')).toBeVisible()

  await page.getByRole('tab', { name: 'Report' }).click()
  await expect(page.getByTestId('report-content')).toContainText(/No heuristic suspicious-zone components were derived/i)

  await page.getByRole('tab', { name: '3D Viewer' }).click()
  await expect(page.getByText(/No suspicious-zone mesh is available because no lesion findings were materialized/i)).toBeVisible()
})
