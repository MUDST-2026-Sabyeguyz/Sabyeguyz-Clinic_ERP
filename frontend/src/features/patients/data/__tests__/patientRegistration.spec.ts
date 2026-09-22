import { describe, expect, it } from 'vitest'
import { formatPhoneNumber, formatThaiDate } from '../patientRegistration'

describe('patient registration formatting', () => {
  it('normalizes a ten-digit phone number', () => {
    expect(formatPhoneNumber('081-234-5678')).toBe('081-234-5678')
    expect(formatPhoneNumber('0812345678')).toBe('081-234-5678')
  })

  it('formats an ISO date as a Thai Buddhist date', () => {
    expect(formatThaiDate('2026-09-22')).toBe('22 กันยายน 2569')
  })
})
