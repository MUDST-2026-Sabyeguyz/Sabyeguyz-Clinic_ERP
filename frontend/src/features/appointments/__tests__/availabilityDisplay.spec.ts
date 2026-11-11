import { describe, expect, it } from 'vitest'
import { displayAvailability } from '../availabilityDisplay'
import type { AppointmentAvailability } from '@/api/client'

const base = {
  date: '2026-11-07',
  openTime: '',
  closeTime: '',
  windowConfigured: false,
  durationMinutes: 60,
  stepMinutes: 30,
  slots: [],
  googleCalendar: 'NOT_CONNECTED',
} satisfies AppointmentAvailability
describe('availability display', () => {
  it('does not invent a duration when the backend returns none', () => {
    const result = displayAvailability({
      ...base, windowConfigured: true, openTime: '09:00', closeTime: '18:00',
      durationMinutes: null,
    }, 60)
    expect(result.slots).toEqual([])
    expect(result.message).toContain('อย่างใดอย่างหนึ่ง')
  })

  it('uses the backend service duration without requiring a branch default', () => {
    const slots = [{ time: '09:00', available: true, reason: null }]
    const result = displayAvailability({
      ...base, windowConfigured: true, durationMinutes: 50, slots,
    }, 60)
    expect(result.slots).toEqual(slots)
    expect(result.message).toBe('')
  })

  it('shows disabled placeholders when opening hours are missing', () => {
    const result = displayAvailability(base, 60)
    expect(result.slots).toHaveLength(9)
    expect(result.slots.every((slot) => !slot.available)).toBe(true)
    expect(result.message).toContain('เวลาเปิด–ปิด')
  })
  it('preserves backend availability including disabled slots', () => {
    const slots = [
      { time: '09:00', available: false, reason: 'มีนัดอื่น' },
      { time: '10:00', available: true, reason: null },
    ]
    expect(displayAvailability({ ...base, windowConfigured: true, slots }, 60).slots).toEqual(slots)
  })
  it('spaces one-hour options without overlapping and preserves their status', () => {
    const slots = ['09:00', '09:30', '10:00', '10:30', '11:00'].map((time) => ({
      time,
      available: time !== '10:00',
      reason: time === '10:00' ? 'มีนัดอื่น' : null,
    }))
    const result = displayAvailability({ ...base, slots }, 60)
    expect(result.slots.map((slot) => slot.time)).toEqual(['09:00', '10:00', '11:00'])
    expect(result.slots[1]?.available).toBe(false)
  })
  it('retains half-hour choices for a half-hour service', () => {
    const slots = ['09:00', '09:30', '10:00'].map((time) => ({
      time,
      available: true,
      reason: null,
    }))
    expect(displayAvailability({ ...base, durationMinutes: 30, slots }, 30).slots).toEqual(slots)
  })
})
