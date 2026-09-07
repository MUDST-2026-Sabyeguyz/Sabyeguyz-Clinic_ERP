import { afterEach, describe, expect, it, vi } from 'vitest'
import { createAppointment, searchAppointments } from '../appointments'
afterEach(() => vi.unstubAllGlobals())
describe('appointments API', () => {
  it('creates an appointment', async () => { vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve({ id: 3 }) })); await createAppointment({ branchCode: 'RP', patientId: 1, therapistId: 1, serviceId: 2, startAt: '2026-09-10T09:00:00+07:00' }); expect(fetch).toHaveBeenCalledWith('http://localhost:8080/api/appointments', expect.objectContaining({ method: 'POST' })) })
  it('builds date and status filters', async () => { vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve({ items: [] }) })); await searchAppointments('2026-09-10', 'SCHEDULED'); expect(fetch).toHaveBeenCalledWith('http://localhost:8080/api/appointments?branchCode=RP&date=2026-09-10&page=0&size=20&status=SCHEDULED') })
})
