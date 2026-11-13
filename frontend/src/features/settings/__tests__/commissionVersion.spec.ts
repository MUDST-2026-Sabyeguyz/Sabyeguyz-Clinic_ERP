import { describe, expect, it } from 'vitest'
import { nextSchemeEffectiveFrom } from '../commissionVersion'

describe('commission scheme version dates', () => {
  it('starts the next version on the day after the previous inclusive end date', () => {
    expect(nextSchemeEffectiveFrom('2026-12-31', '2026-10-31')).toBe('2027-01-01')
  })

  it('starts a new open-ended scheme on the first day of the next clinic month', () => {
    expect(nextSchemeEffectiveFrom(null, '2026-10-31')).toBe('2026-11-01')
  })
})
