import { afterEach, describe, expect, it, vi } from 'vitest'
import { createAppointment, rescheduleAppointment, searchAppointments, updateAppointmentStatus } from '../appointments'
afterEach(() => vi.unstubAllGlobals())
describe('appointments API', () => {
  it('creates an appointment', async () => { vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve({ id: 3 }) })); await createAppointment({ branchCode: 'RP', patientId: 1, therapistId: 1, serviceId: 2, startAt: '2026-09-10T09:00:00+07:00' }); expect(fetch).toHaveBeenCalledWith('http://localhost:8080/api/appointments', expect.objectContaining({ method: 'POST' })) })
  it('builds date and status filters', async () => { vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve({ items: [] }) })); await searchAppointments('2026-09-10', 'SCHEDULED'); expect(fetch).toHaveBeenCalledWith('http://localhost:8080/api/appointments?branchCode=RP&date=2026-09-10&page=0&size=20&status=SCHEDULED') })
  it('cancels with a reason', async () => { vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve({ id: 3 }) })); await updateAppointmentStatus(3, 'cancel', 'ผู้ป่วยขอเลื่อน'); expect(fetch).toHaveBeenCalledWith('http://localhost:8080/api/appointments/3/cancel', expect.objectContaining({ method: 'POST', body: '{"reason":"ผู้ป่วยขอเลื่อน"}' })) })
  it('reschedules an appointment', async () => { vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve({ id: 3 }) })); await rescheduleAppointment(3, '2026-09-12T09:00:00+07:00', 2); expect(fetch).toHaveBeenCalledWith('http://localhost:8080/api/appointments/3', expect.objectContaining({ method: 'PATCH' })) })
})
