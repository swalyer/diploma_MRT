import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { expect, test, type Page } from '@playwright/test'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const SEEDED_CASE_SLUG = 'ct-single-lesion-001'
const SEEDED_CASE_CATEGORY = 'SINGLE_LESION'

async function importSeededCaseAndOpen(page: Page) {
  const manifestPath = path.resolve(__dirname, `../../../demo-data/manifests/${SEEDED_CASE_SLUG}.json`)
  const manifestText = fs.readFileSync(manifestPath, 'utf-8')

  await page.goto('/login')
  await page.getByTestId('login-email').fill('admin@demo.local')
  await page.getByTestId('login-password').fill('Admin123!')
  await page.getByTestId('login-submit').click()

  await expect(page).toHaveURL(/\/cases$/)
  await page.getByRole('button', { name: 'Admin' }).click()
  await expect(page).toHaveURL(/\/admin$/)

  const readyDemoRow = page.getByRole('listitem').filter({ hasText: new RegExp(`${SEEDED_CASE_SLUG} · CT · ${SEEDED_CASE_CATEGORY}`, 'i') })
  try {
    await expect.poll(async () => await readyDemoRow.count(), { timeout: 5_000 }).toBeGreaterThan(0)
  } catch {
    // Fall through to import workflow when the seeded case is not already present.
  }
  if (await readyDemoRow.count()) {
    await readyDemoRow.getByRole('link', { name: 'Open' }).click()
    await expect(page).toHaveURL(/\/cases\/\d+$/)
    return
  }

  await page.getByLabel('Demo manifest JSON').fill(manifestText)
  await page.getByRole('button', { name: 'Import demo manifest' }).click()

  const importOpenLink = page.getByRole('link', { name: /Open case #/ })
  await expect
    .poll(async () => {
      if (await importOpenLink.count()) return 'import-link'
      if (await readyDemoRow.count()) return 'ready-row'
      return 'pending'
    }, { timeout: 15_000 })
    .not.toBe('pending')

  if (await importOpenLink.count()) {
    await importOpenLink.click()
  } else {
    await readyDemoRow.getByRole('link', { name: 'Open' }).click()
  }
  await expect(page).toHaveURL(/\/cases\/\d+$/)
}

test('seeded demo case exposes 2D, 3D, and artifact download workflows', async ({ page }) => {
  await importSeededCaseAndOpen(page)

  await expect(page.getByText(/Seeded demo ·/)).toBeVisible()
  await expect(page.getByText('Result: seeded import')).toBeVisible()
  await expect(page.getByTestId('run-pipeline-button')).toBeDisabled()

  await page.getByRole('tab', { name: '2D Imaging' }).click()
  await expect(page.getByTestId('viewer-2d-canvas')).toBeVisible()
  await expect(page.getByText(/Slice \d+\/\d+/)).toBeVisible()
  await expect(page.getByText(/NIfTI artifact-backed rendering/i)).toBeVisible()

  await page.getByRole('tab', { name: '3D Viewer' }).click()
  await expect(page.getByTestId('viewer-3d-root')).toBeVisible()
  await expect(page.getByTestId('viewer-3d-format-alert')).toBeVisible()
  await expect(page.getByTestId('viewer-3d-canvas')).toBeVisible()
  await expect(page.getByText('Suspicious zones')).toBeVisible()

  await page.getByRole('tab', { name: 'Artifacts / Technical' }).click()
  await expect(page.getByText('ORIGINAL_STUDY')).toBeVisible()
  await expect(page.getByText('LESION_MESH')).toBeVisible()

  const downloadPromise = page.waitForEvent('download')
  await page.getByTestId('artifact-download-ORIGINAL_STUDY').click()
  const download = await downloadPromise
  expect(download.suggestedFilename()).toBe('input.nii.gz')
})
