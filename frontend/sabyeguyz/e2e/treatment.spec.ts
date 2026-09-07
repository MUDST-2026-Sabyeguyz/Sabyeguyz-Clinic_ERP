import { expect, test } from '@playwright/test'

test('validates and saves a treatment draft', async ({ page }) => {
  await page.goto('/treatment')
  await page.getByRole('button', { name: 'บันทึกการรักษา', exact: true }).click()
  await expect(page.getByRole('alert')).toHaveText('กรุณากรอกข้อมูลที่จำเป็นให้ครบถ้วน')
  await page.getByLabel('Patient ID *', { exact: true }).fill('1')
  await page.getByLabel('Appointment ID *', { exact: true }).fill('9')
  await page.getByLabel('บริการที่ได้รับ *', { exact: true }).selectOption({ label: 'กายภาพบำบัดทั่วไป' })
  await page.getByLabel('รายละเอียดการรักษา *', { exact: true }).fill('ประเมินอาการและบันทึกการรักษาเบื้องต้น')
  await page.getByRole('button', { name: 'บันทึกการรักษา', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('รอ Backend Treatment API')
})
