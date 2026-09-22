import { test, expect } from '@playwright/test'

test('shows the staff login page', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'เข้าสู่ระบบ' })).toBeVisible()
})
