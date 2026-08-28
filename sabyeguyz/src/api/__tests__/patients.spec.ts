import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  getPatientProfile,
  registerPatient,
  searchPatients,
  type PatientRegistrationPayload,
} from '../patients'

afterEach(() => vi.unstubAllGlobals())

describe('registerPatient', () => {
  it('returns the generated HN', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue({
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

describe('patient queries', () => {
  it('searches patients with the backend pagination contract', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue({
          ok: true,
          json: () =>
            Promise.resolve({ items: [], page: 1, size: 20, totalItems: 0, totalPages: 0 }),
        }),
    )
    const result = await searchPatients('สมชาย', 1)
    expect(result.page).toBe(1)
    expect(fetch).toHaveBeenCalledWith(
      'http://localhost:8080/api/patients?query=%E0%B8%AA%E0%B8%A1%E0%B8%8A%E0%B8%B2%E0%B8%A2&page=1&size=20',
    )
  })

  it('loads a patient profile by database id', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue({ ok: true, json: () => Promise.resolve({ id: 7, hn: '2601120001' }) }),
    )
    const result = await getPatientProfile(7)
    expect(result.id).toBe(7)
    expect(fetch).toHaveBeenCalledWith('http://localhost:8080/api/patients/7')
  })
})
