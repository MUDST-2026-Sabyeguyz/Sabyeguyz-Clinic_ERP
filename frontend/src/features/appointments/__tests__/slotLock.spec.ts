import { afterEach, describe, expect, it, vi } from 'vitest'
import { appointmentApi } from '@/api/client'
import { slotLockLabel } from '../slotLock'

afterEach(() => vi.unstubAllGlobals())

describe('slotLockLabel', () => {
  it('says why a time is locked', () => {
    expect(slotLockLabel('มีนัดอื่นของนักกายภาพ')).toBe('จองแล้ว')
    expect(slotLockLabel('ผู้ป่วยมีนัดอื่นในเวลานี้')).toBe('ผู้ป่วยมีนัด')
    expect(slotLockLabel('เลยเวลาแล้ว')).toBe('เลยเวลาแล้ว')
  })

  it('falls back to a plain locked label', () => {
    expect(slotLockLabel('ติด Busy ใน Google Calendar')).toBe('ไม่ว่าง')
    expect(slotLockLabel(undefined)).toBe('ไม่ว่าง')
    expect(slotLockLabel(null)).toBe('ไม่ว่าง')
  })
})

describe('appointmentApi.availability', () => {
  const answer = {
    date: '2026-11-20',
    slots: [{ time: '10:00', available: false, reason: 'ผู้ป่วยมีนัดอื่นในเวลานี้' }],
  }

  it('asks for the free times together with the patient so the patient’s own appointments lock the list', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, text: () => Promise.resolve(JSON.stringify(answer)) })
    vi.stubGlobal('fetch', fetchMock)

    const result = await appointmentApi.availability({
      branchCode: '01',
      therapistId: 4,
      date: '2026-11-20',
      serviceId: 2,
      excludeAppointmentId: 9,
      patientId: 7,
    })

    const url = new URL(String(fetchMock.mock.calls[0]![0]), 'http://localhost')
    expect(url.pathname).toBe('/api/appointments/availability')
    expect(url.searchParams.get('patientId')).toBe('7')
    expect(url.searchParams.get('therapistId')).toBe('4')
    expect(url.searchParams.get('excludeAppointmentId')).toBe('9')
    expect(result.slots[0]!.available).toBe(false)
  })

  it('leaves the patient out when the booking has none yet (a new patient)', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, text: () => Promise.resolve(JSON.stringify(answer)) })
    vi.stubGlobal('fetch', fetchMock)

    await appointmentApi.availability({ branchCode: '01', therapistId: 4, date: '2026-11-20', patientId: 0 })

    const url = new URL(String(fetchMock.mock.calls[0]![0]), 'http://localhost')
    expect(url.searchParams.has('patientId')).toBe(false)
  })
})
