import { afterEach, describe, expect, it, vi } from 'vitest'
import { registerPatient, type PatientRegistrationPayload } from '../patients'
import { patientApi } from '../client'

afterEach(() => {
  vi.unstubAllGlobals()
  localStorage.removeItem('authToken')
})

describe('patientApi.identityLookup', () => {
  it('sends an authenticated exact ID query and preserves the minimal lookup result', async () => {
    localStorage.setItem('authToken', 'lookup-token')
    const response = { exists: true, patient: { id: 8, hn: '2610000008', fullName: 'John Middle Smith' } }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, text: async () => JSON.stringify(response) }))
    expect(await patientApi.identityLookup('1100000000931')).toEqual(response)
    const [url, init] = vi.mocked(fetch).mock.calls[0]!
    expect(url).toBe('/api/patients/identity-lookup?nationalId=1100000000931')
    expect(new Headers(init?.headers).get('Authorization')).toBe('Bearer lookup-token')
    expect(vi.mocked(fetch).mock.calls).toHaveLength(1)
  })

  it('encodes input as one query value and surfaces validation errors', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 400,
      text: async () => JSON.stringify({ message: 'เลขบัตรประชาชนต้องเป็นตัวเลข 13 หลัก' }) }))
    await expect(patientApi.identityLookup('123&branchCode=02')).rejects.toThrow('เลขบัตรประชาชน')
    expect(vi.mocked(fetch).mock.calls[0]![0]).toBe('/api/patients/identity-lookup?nationalId=123%26branchCode%3D02')
  })
})

describe('registerPatient', () => {
  it('returns the generated HN', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        text: () => Promise.resolve(JSON.stringify({
            id: 1,
            hn: 'R9-000001',
            fullName: 'สมชาย',
            phone: '0812345678',
            active: true,
          })),
      }),
    )
    const result = await registerPatient({} as PatientRegistrationPayload)
    expect(result.hn).toBe('R9-000001')
    expect(fetch).toHaveBeenCalledWith('/api/patients', expect.objectContaining({ method: 'POST' }))
  })

  it('exposes the API error message', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue({ ok: false, status: 400, text: () => Promise.resolve(JSON.stringify({ message: 'ข้อมูลซ้ำ' })) }),
    )
    await expect(registerPatient({} as PatientRegistrationPayload)).rejects.toThrow('ข้อมูลซ้ำ')
  })

  it('adds the logged-in JWT to protected requests', async () => {
    localStorage.setItem('authToken', 'jwt-test')
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      text: () => Promise.resolve(JSON.stringify({ id: 1, hn: 'R9-000001', fullName: 'สมชาย', phone: '0812345678', active: true })),
    }))
    await registerPatient({} as PatientRegistrationPayload)
    const [, init] = vi.mocked(fetch).mock.calls[0]!
    expect(new Headers(init?.headers).get('Authorization')).toBe('Bearer jwt-test')
    localStorage.removeItem('authToken')
  })
})
