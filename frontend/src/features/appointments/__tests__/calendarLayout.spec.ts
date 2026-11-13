import { describe, expect, it } from 'vitest'
import { weekDates, layoutEvents, therapistColor, activeAppointments } from '../calendarLayout'
import type { Appointment } from '@/api/client'
describe('shared calendar layout', () => {
  it('removes cancelled appointments and other branches before layout', () => {
    const items = [
      { id: 1, branchCode: '01', status: 'CANCELLED' },
      { id: 2, branchCode: '01', status: 'CONFIRMED' },
      { id: 3, branchCode: '02', status: 'CONFIRMED' },
    ] as Appointment[]
    expect(activeAppointments(items, '01').map((item) => item.id)).toEqual([2])
  })
  it('includes Sunday and crosses month boundaries starting on Monday', () => {
    expect(weekDates('2026-11-01')).toEqual([
      '2026-10-26',
      '2026-10-27',
      '2026-10-28',
      '2026-10-29',
      '2026-10-30',
      '2026-10-31',
      '2026-11-01',
    ])
  })
  it('keeps all simultaneous appointments, assigns separate lanes and reuses lanes at touching endpoints', () => {
    const result = layoutEvents([
      { id: 1, start: 540, end: 600 },
      { id: 2, start: 570, end: 630 },
      { id: 3, start: 600, end: 660 },
    ])
    expect(result.map((row) => row.id)).toEqual([1, 2, 3])
    expect(result.map((row) => row.lanes)).toEqual([2, 2, 2])
    expect(result[0]!.lane).not.toBe(result[1]!.lane)
    expect(result[2]!.lane).toBe(result[0]!.lane)
  })
  it('does not shrink independent appointments to the lane count of a previous overlap group', () => {
    const result = layoutEvents([
      { id: 1, start: 540, end: 600 },
      { id: 2, start: 540, end: 600 },
      { id: 3, start: 660, end: 720 },
    ])
    expect(result[2]!.lanes).toBe(1)
    expect(therapistColor(2)).toEqual(therapistColor(2))
    expect(therapistColor(2)).not.toEqual(therapistColor(3))
  })
})
