import { describe, expect, it } from 'vitest'
import { sanitizePatientName } from '../newPatientName'

describe('sanitizePatientName', () => {
  it('removes Arabic and Thai digits while preserving name characters', () => {
    expect(sanitizePatientName('จันทร์555John๙')).toBe('จันทร์John')
  })
})
