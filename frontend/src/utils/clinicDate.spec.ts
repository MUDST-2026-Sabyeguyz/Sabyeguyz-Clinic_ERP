import { describe, expect, it } from 'vitest'
import { clinicDate, clinicMonthEnd } from './clinicDate'

describe('clinic calendar dates', () => {
  it('changes the clinic date at midnight in Bangkok, independent of UTC date', () => {
    expect(clinicDate(new Date('2026-10-07T16:59:59.999Z'))).toBe('2026-10-07')
    expect(clinicDate(new Date('2026-10-07T17:00:00.000Z'))).toBe('2026-10-08')
  })

  it('calculates month ends without converting local midnight to a UTC date', () => {
    expect(clinicMonthEnd(2024, 2)).toBe('2024-02-29')
    expect(clinicMonthEnd(2026, 10)).toBe('2026-10-31')
  })
})
