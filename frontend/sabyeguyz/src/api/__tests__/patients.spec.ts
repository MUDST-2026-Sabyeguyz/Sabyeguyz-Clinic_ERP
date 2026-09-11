import { afterEach, describe, expect, it, vi } from 'vitest'
import { registerPatient, type PatientRegistrationPayload } from '../patients'

afterEach(() => vi.unstubAllGlobals())

describe('registerPatient', () => {
  it('returns the generated HN', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: () =>
          Promise.resolve({
            id: 1,
            hn: 'RP-000001',
            fullName: 'สมชาย',
            phone: '0812345678',
            active: true,
          }),
      }),
    )
    const result = await registerPatient({} as PatientRegistrationPayload)
    expect(result.hn).toBe('RP-000001')
    expect(fetch).toHaveBeenCalledWith(
      'http://localhost:8080/api/patients',
      expect.objectContaining({ method: 'POST' }),
    )
  })

  it('exposes the API error message', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue({ ok: false, json: () => Promise.resolve({ message: 'ข้อมูลซ้ำ' }) }),
    )
    await expect(registerPatient({} as PatientRegistrationPayload)).rejects.toThrow('ข้อมูลซ้ำ')
  })
})
