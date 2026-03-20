import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { expect, test } from '@playwright/test'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

test('doctor can create, upload, process, and review a case', async ({ page }) => {
  const fixturePath = path.resolve(__dirname, '../fixtures/mock-study.nii.gz')
  const pseudoId = `E2E-${Date.now()}`

  await page.goto('/login')
  await page.getByTestId('login-email').fill('doctor@demo.local')
  await page.getByTestId('login-password').fill('Admin123!')
  await page.getByTestId('login-submit').click()

  await expect(page).toHaveURL(/\/cases$/)
  await page.getByRole('button', { name: 'Intake' }).click()
  await expect(page).toHaveURL(/\/cases\/new$/)

  await page.getByTestId('create-case-pseudo-id').fill(pseudoId)
  await page.locator('[data-testid="study-file-input"]').setInputFiles(fixturePath)
  await page.getByTestId('create-case-submit').click()

  await expect(page).toHaveURL(/\/cases\/\d+$/)
  await expect(page.getByTestId('case-status-chip')).toContainText('UPLOADED')

  await page.getByTestId('run-pipeline-button').click()

  await expect.poll(async () => page.getByTestId('case-status-chip').textContent()).toContain('COMPLETED')
  await expect.poll(async () => page.getByTestId('inference-status-chip').textContent()).toContain('COMPLETED')

  await page.getByRole('tab', { name: 'Report' }).click()
  await expect(page.getByTestId('report-content')).not.toContainText('Report unavailable')

  await page.getByRole('tab', { name: 'Artifacts / Technical' }).click()
  await expect(page.getByText('ORIGINAL_STUDY')).toBeVisible()
  await expect(page.getByText('ENHANCED_VOLUME')).toBeVisible()

  await page.getByRole('tab', { name: '3D Viewer' }).click()
  await expect(page.getByText('Format support: GLB/GLTF only')).toBeVisible()
})
