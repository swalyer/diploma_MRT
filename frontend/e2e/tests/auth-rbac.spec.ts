import { expect, test } from '@playwright/test'
import { loginAs } from './helpers'

test.describe('Authentication and role-based access', () => {
  test('shows an error and stays on /login for a bad password', async ({ page }) => {
    await page.goto('/login')
    await page.getByTestId('login-email').fill('admin@demo.local')
    await page.getByTestId('login-password').fill('definitely-wrong')
    await page.getByTestId('login-submit').click()
    await expect(page.getByText(/Authentication failed/i)).toBeVisible()
    await expect(page).toHaveURL(/\/login$/)
  })

  test('redirects an unauthenticated visitor to /login', async ({ page }) => {
    await page.goto('/cases/1')
    await expect(page).toHaveURL(/\/login$/)
  })

  test('doctor does not see the Admin navigation', async ({ page }) => {
    await loginAs(page, 'doctor')
    await expect(page.getByRole('button', { name: 'Cases' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Admin' })).toHaveCount(0)
  })

  test('admin sees the Admin navigation and can open the console', async ({ page }) => {
    await loginAs(page, 'admin')
    await page.getByRole('button', { name: 'Admin' }).click()
    await expect(page).toHaveURL(/\/admin$/)
  })

  test('logout returns to the login screen and protects routes again', async ({ page }) => {
    await loginAs(page, 'doctor')
    await page.getByRole('button', { name: 'Logout' }).click()
    await expect(page).toHaveURL(/\/login$/)
    await page.goto('/cases')
    await expect(page).toHaveURL(/\/login$/)
  })
})
