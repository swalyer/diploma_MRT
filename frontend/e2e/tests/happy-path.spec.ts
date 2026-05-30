import { expect, test } from '@playwright/test'
import { apiLogin, createAndProcessCase, loginAs } from './helpers'

// Full live happy path: a doctor takes a study from intake through processing
// to a reviewed result across every surface (report, PDF, 2D, 3D).
test.describe('CT happy path (create -> process -> review)', () => {
  test('processes an uploaded study and exposes results on every tab', async ({ page, request }) => {
    await loginAs(page, 'doctor')
    const caseId = await createAndProcessCase(page, 'CT')

    await expect(page.getByTestId('inference-status-chip')).toContainText('COMPLETED')

    // Report tab + structured sections
    await page.getByRole('tab', { name: 'Report' }).click()
    await expect(page.getByTestId('report-content')).not.toContainText('Report unavailable')

    // Signable PDF from the live endpoint
    const token = await apiLogin(request, 'doctor')
    const pdf = await request.get(`/api/cases/${caseId}/report.pdf`, { headers: { Authorization: `Bearer ${token}` } })
    expect(pdf.status()).toBe(200)
    expect(pdf.headers()['content-type']).toContain('pdf')
    const head = (await pdf.body()).subarray(0, 4).toString()
    expect(head).toBe('%PDF')

    // Artifacts present
    await page.getByRole('tab', { name: 'Artifacts / Technical' }).click()
    await expect(page.getByText('ORIGINAL_STUDY')).toBeVisible()

    // 2D viewer renders an artifact-backed canvas (not the fallback alert)
    await page.getByRole('tab', { name: '2D Imaging' }).click()
    await expect(page.getByTestId('viewer-2d-canvas')).toBeVisible()
    await expect(page.getByText(/No NIfTI-compatible|Unable to render/i)).toHaveCount(0)

    // 3D viewer renders the mesh canvas
    await page.getByRole('tab', { name: '3D Viewer' }).click()
    await expect(page.getByTestId('viewer-3d-canvas')).toBeVisible()
  })
})
