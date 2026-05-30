import { expect, test } from '@playwright/test'
import { apiLogin } from './helpers'

// Contract-level negative scenarios exercised directly against the API the
// frontend consumes, so security regressions are caught independently of UI.
test.describe('API negative scenarios', () => {
  test('rejects login with a wrong password', async ({ request }) => {
    const res = await request.post('/api/auth/login', {
      data: { email: 'admin@demo.local', password: 'definitely-wrong' },
    })
    expect(res.status()).toBeGreaterThanOrEqual(400)
    expect(res.status()).toBeLessThan(500)
  })

  test('blocks unauthenticated access to cases', async ({ request }) => {
    const res = await request.get('/api/cases')
    expect([401, 403]).toContain(res.status())
  })

  test('returns 404 for a non-existent case', async ({ request }) => {
    const token = await apiLogin(request, 'admin')
    const res = await request.get('/api/cases/999999', { headers: { Authorization: `Bearer ${token}` } })
    expect(res.status()).toBe(404)
  })

  test('forbids a doctor from the admin import endpoint', async ({ request }) => {
    const token = await apiLogin(request, 'doctor')
    const res = await request.post('/api/admin/demo-cases/import', {
      headers: { Authorization: `Bearer ${token}` },
      data: { schemaVersion: 'v1', caseSlug: 'should-not-pass' },
    })
    expect(res.status()).toBe(403)
  })

  test('rejects a non-NIfTI upload with a 4xx', async ({ request }) => {
    const token = await apiLogin(request, 'doctor')
    const created = await request.post('/api/cases', {
      headers: { Authorization: `Bearer ${token}` },
      data: { patientPseudoId: `NEG-${Date.now()}`, modality: 'CT' },
    })
    expect(created.status()).toBe(201)
    const caseId = (await created.json()).id
    const res = await request.post(`/api/cases/${caseId}/upload`, {
      headers: { Authorization: `Bearer ${token}` },
      multipart: { file: { name: 'notscan.txt', mimeType: 'text/plain', buffer: Buffer.from('not a nifti') } },
    })
    expect(res.status()).toBeGreaterThanOrEqual(400)
    expect(res.status()).toBeLessThan(500)
  })
})
