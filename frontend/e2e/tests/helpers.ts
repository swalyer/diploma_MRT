import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { APIRequestContext, Page, expect } from '@playwright/test'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

export const FIXTURE_NIFTI = path.resolve(__dirname, '../../../example4d.nii.gz')
export const MANIFEST_DIR = path.resolve(__dirname, '../../../demo-data')

export const CREDENTIALS = {
  admin: { email: 'admin@demo.local', password: 'Admin123!' },
  doctor: { email: 'doctor@demo.local', password: 'Admin123!' },
} as const

export type Role = keyof typeof CREDENTIALS

/** Log in through the UI and land on the cases dashboard. */
export async function loginAs(page: Page, role: Role) {
  const { email, password } = CREDENTIALS[role]
  await page.goto('/login')
  await page.getByTestId('login-email').fill(email)
  await page.getByTestId('login-password').fill(password)
  await page.getByTestId('login-submit').click()
  await expect(page).toHaveURL(/\/cases$/)
}

/** Obtain a JWT via the auth API (for direct API assertions). */
export async function apiLogin(request: APIRequestContext, role: Role): Promise<string> {
  const res = await request.post('/api/auth/login', { data: CREDENTIALS[role] })
  expect(res.ok(), `login ${role} -> ${res.status()}`).toBeTruthy()
  return (await res.json()).token as string
}

/** Create a case, upload the NIfTI fixture, run the pipeline, and wait for COMPLETED. Returns the case id. */
export async function createAndProcessCase(page: Page, modality: 'CT' | 'MRI' = 'CT'): Promise<number> {
  await page.goto('/cases/new')
  await page.getByTestId('create-case-pseudo-id').fill(`E2E-${modality}-${Date.now()}`)
  if (modality !== 'CT') {
    // The modality select is a native input behind MUI; set it directly.
    await page.getByTestId('create-case-modality').selectOption(modality).catch(() => {})
  }
  await page.locator('[data-testid="study-file-input"]').setInputFiles(FIXTURE_NIFTI)
  await page.getByTestId('create-case-submit').click()
  await expect(page).toHaveURL(/\/cases\/\d+$/)
  const caseId = Number(page.url().split('/').pop())

  await page.getByTestId('run-pipeline-button').click()
  await expect.poll(async () => (await page.getByTestId('case-status-chip').textContent()) ?? '', {
    timeout: 90_000,
    intervals: [1000, 2000, 3000],
  }).toContain('COMPLETED')
  return caseId
}

/** Import a seeded demo manifest via the admin API; returns the created/updated case id. */
export async function importSeededCase(request: APIRequestContext, adminToken: string, manifestRelPath: string): Promise<number> {
  const fs = await import('node:fs')
  const manifest = JSON.parse(fs.readFileSync(path.join(MANIFEST_DIR, manifestRelPath), 'utf-8'))
  const res = await request.post('/api/admin/demo-cases/import', {
    headers: { Authorization: `Bearer ${adminToken}` },
    data: manifest,
  })
  expect(res.ok(), `import ${manifestRelPath} -> ${res.status()}`).toBeTruthy()
  return (await res.json()).caseId as number
}
