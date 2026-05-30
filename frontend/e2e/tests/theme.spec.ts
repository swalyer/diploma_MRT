import { expect, test } from '@playwright/test'

// The theme is a zustand store persisted to localStorage; verify the toggle
// flips the mode and survives a reload, on the public login screen.
test.describe('Theme toggle', () => {
  test('toggles light/dark and persists across reload', async ({ page }) => {
    await page.goto('/login')
    const toggle = page.getByLabel('toggle color theme')
    await expect(toggle).toBeVisible()

    const initial = await page.evaluate(() => localStorage.getItem('mrt.theme.mode'))
    await toggle.click()
    await page.waitForTimeout(200)
    const afterToggle = await page.evaluate(() => localStorage.getItem('mrt.theme.mode'))
    expect(afterToggle).not.toBe(initial)
    expect(['light', 'dark']).toContain(afterToggle)

    await page.reload()
    const afterReload = await page.evaluate(() => localStorage.getItem('mrt.theme.mode'))
    expect(afterReload).toBe(afterToggle)
  })
})
