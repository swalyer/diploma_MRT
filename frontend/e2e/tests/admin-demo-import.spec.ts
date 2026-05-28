import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { expect, test } from '@playwright/test'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

test('admin can import a seeded demo manifest and open the case', async ({ page }) => {
  const manifestPath = path.resolve(__dirname, '../../../demo-data/manifests/ct-multifocal-001.json')
  const manifestText = fs.readFileSync(manifestPath, 'utf-8')

  await page.goto('/login')
  await page.getByTestId('login-email').fill('admin@demo.local')
  await page.getByTestId('login-password').fill('Admin123!')
  await page.getByTestId('login-submit').click()

  await expect(page).toHaveURL(/\/cases$/)
  await page.getByRole('button', { name: 'Admin' }).click()
  await expect(page).toHaveURL(/\/admin$/)

  await page.getByLabel('Demo manifest JSON').fill(manifestText)
  await page.getByRole('button', { name: 'Import demo manifest' }).click()

  await expect(page.getByText(/seeded case ct-multifocal-001/i)).toBeVisible()
  await page.getByRole('link', { name: /Open case #/ }).click()

  await expect(page).toHaveURL(/\/cases\/\d+$/)
  await expect(page.getByText(/Seeded demo ·/)).toBeVisible()
  await expect(page.getByText('Result: seeded import')).toBeVisible()
  await expect(page.getByTestId('run-pipeline-button')).toBeDisabled()

  await page.getByRole('tab', { name: 'Report' }).click()
  await expect(page.getByTestId('report-content')).not.toContainText('Report unavailable')
})
