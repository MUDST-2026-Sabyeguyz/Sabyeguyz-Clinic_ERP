import { expect, test } from '@playwright/test'

test('creates an appointment from the appointment form', async ({ page }) => {
  await page.route('**/api/appointments?**', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 }) })
  })
  await page.route('**/api/appointments', async (route) => {
    if (route.request().method() === 'POST') {
      await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify({ id: 1, status: 'CONFIRMED', patient: { fullName: 'สมชาย ทดสอบ' }, therapist: { fullName: 'นักกายภาพบำบัด คนที่หนึ่ง' }, service: { name: 'กายภาพบำบัดทั่วไป 30 นาที', durationMinutes: 30 }, startAt: '2026-09-10T02:00:00Z', endAt: '2026-09-10T02:30:00Z' }) })
    } else await route.continue()
  })
  await page.goto('/appointments')
  await page.getByLabel('Patient ID *', { exact: true }).fill('1')
  await page.getByRole('button', { name: 'สร้างนัดหมาย', exact: true }).click()
  await expect(page.getByRole('status')).toHaveText('สร้างนัดหมายสำเร็จ')
})

test('moves a confirmed appointment through arrival, service, and completion', async ({ page }) => {
  let currentStatus = 'CONFIRMED'
  const appointment = { id: 9, status: currentStatus, patient: { fullName: 'ผู้ป่วยทดสอบ' }, therapist: { fullName: 'นักกายภาพบำบัด คนที่หนึ่ง' }, service: { name: 'กายภาพบำบัดทั่วไป 30 นาที' }, startAt: '2026-09-10T02:00:00Z', endAt: '2026-09-10T02:30:00Z' }
  await page.route('**/api/appointments?**', async route => { await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ items: [{ ...appointment, status: currentStatus }], page: 0, size: 20, totalItems: 1, totalPages: 1 }) }) })
  await page.route('**/api/appointments/9/check-in', async route => { currentStatus = 'ARRIVED'; await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ ...appointment, status: currentStatus }) }) })
  await page.route('**/api/appointments/9/start-service', async route => { currentStatus = 'IN_SERVICE'; await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ ...appointment, status: currentStatus }) }) })
  await page.route('**/api/appointments/9/complete', async route => { currentStatus = 'COMPLETED'; await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ ...appointment, status: currentStatus }) }) })
  await page.goto('/appointments')
  await page.getByRole('button', { name: 'เช็กอิน', exact: true }).click()
  await expect(page.getByText('เปลี่ยนสถานะสำเร็จ')).toBeVisible()
  await page.getByRole('button', { name: 'เริ่มบริการ', exact: true }).click()
  await expect(page.getByText('เปลี่ยนสถานะสำเร็จ')).toBeVisible()
  await page.getByRole('button', { name: 'เสร็จสิ้น', exact: true }).click()
  await expect(page.getByText('เปลี่ยนสถานะสำเร็จ')).toBeVisible()
})

test('marks a confirmed appointment as no show', async ({ page }) => {
  let currentStatus = 'CONFIRMED'
  const appointment = { id: 9, status: currentStatus, patient: { fullName: 'ผู้ป่วยทดสอบ' }, therapist: { fullName: 'นักกายภาพบำบัด คนที่หนึ่ง' }, service: { name: 'กายภาพบำบัดทั่วไป 30 นาที' }, startAt: '2026-09-10T02:00:00Z', endAt: '2026-09-10T02:30:00Z' }
  await page.route('**/api/appointments?**', async route => { await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ items: [{ ...appointment, status: currentStatus }], page: 0, size: 20, totalItems: 1, totalPages: 1 }) }) })
  await page.route('**/api/appointments/9/no-show', async route => { currentStatus = 'NO_SHOW'; await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ ...appointment, status: currentStatus }) }) })
  await page.goto('/appointments')
  await page.getByRole('button', { name: 'ไม่มาตามนัด', exact: true }).click()
  await expect(page.getByText('เปลี่ยนสถานะสำเร็จ')).toBeVisible()
  await expect(page.locator('.patient-result-card strong')).toHaveText('ไม่มาตามนัด')
})
