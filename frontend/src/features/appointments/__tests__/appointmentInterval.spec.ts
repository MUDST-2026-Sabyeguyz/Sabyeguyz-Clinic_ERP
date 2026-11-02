import { describe, expect, it } from 'vitest'
import { appointmentEndFields, appointmentInterval, appointmentLocalFields } from '../appointmentInterval'

describe('appointment interval in Asia/Bangkok', () => {
  it('converts UTC instants across midnight without using the device timezone or service duration', () => {
    expect(appointmentLocalFields('2026-10-30T18:30:00Z')).toEqual({
      date: '2026-10-31',
      time: '01:30',
    })
    expect(appointmentLocalFields('2026-10-30T20:15:00Z')).toEqual({
      date: '2026-10-31',
      time: '03:15',
    })
  })

  it('sends explicit Bangkok offsets and preserves a selected overnight end', () => {
    expect(appointmentInterval('2026-10-30', '23:30', '2026-10-31', '00:30')).toEqual({
      startAt: '2026-10-30T23:30:00+07:00',
      endAt: '2026-10-31T00:30:00+07:00',
    })
  })

  it('calculates the default end across midnight in Bangkok', () => {
    expect(appointmentEndFields('2026-10-30', '23:30', 60)).toEqual({
      date: '2026-10-31',
      time: '00:30',
    })
  })

  it.each([
    ['2026-10-30', '09:00', '', ''],
    ['2026-10-30', '09:00', '2026-10-30', '09:00'],
    ['2026-10-30', '09:00', '2026-10-30', '08:59'],
    ['2026-10-30', '09:00', '2026-10-29', '10:00'],
    ['2026-02-30', '09:00', '2026-03-01', '10:00'],
  ])('rejects missing, invalid or non-positive intervals: %s %s → %s %s', (...args) => {
    expect(() => appointmentInterval(...args)).toThrow()
  })
})
