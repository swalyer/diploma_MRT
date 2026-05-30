import { expect, test } from '@playwright/test'
import { apiLogin, importSeededCase, loginAs } from './helpers'

// The interactive findings panel drives the shared selection that the 2D/3D
// viewers react to. Use the multifocal seeded case for many deterministic rows.
test.describe('Interactive findings panel', () => {
  test('lists findings and navigates to the 2D viewer on locate', async ({ page, request }) => {
    const adminToken = await apiLogin(request, 'admin')
    const caseId = await importSeededCase(request, adminToken, 'manifests/ct-multifocal-001.json')

    await loginAs(page, 'admin')
    await page.goto(`/cases/${caseId}`)

    // Overview findings panel renders multiple rows.
    const rows = page.locator('[data-testid^="finding-row-"]')
    await expect(rows.first()).toBeVisible()
    expect(await rows.count()).toBeGreaterThan(1)

    // Selecting a row marks it as selected (aria/visual handled by the app).
    await rows.first().click()

    // "View in 2D" jumps to the imaging tab with an artifact-backed canvas.
    await page.locator('[data-testid^="finding-locate-"]').first().click()
    await expect(page.getByTestId('viewer-2d-canvas')).toBeVisible()
  })

  test('renders finding labels in the report tab', async ({ page, request }) => {
    const adminToken = await apiLogin(request, 'admin')
    const caseId = await importSeededCase(request, adminToken, 'manifests/ct-multifocal-001.json')

    await loginAs(page, 'admin')
    await page.goto(`/cases/${caseId}`)
    await page.getByRole('tab', { name: 'Report' }).click()
    await expect(page.locator('[data-testid^="finding-row-"]').first()).toBeVisible()
  })
})
