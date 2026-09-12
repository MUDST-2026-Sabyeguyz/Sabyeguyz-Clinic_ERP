import { afterEach, describe, expect, it, vi } from 'vitest'
import { registerPatient, type PatientRegistrationPayload } from '../patients'

afterEach(() => vi.unstubAllGlobals())

describe('registerPatient', () => {
  it('returns the generated HN', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        text: () => Promise.resolve(JSON.stringify({
            id: 1,
            hn: 'RP-000001',
            fullName: 'สมชาย',
            phone: '0812345678',
            active: true,
          })),
      }),
    )
    const result = await registerPatient({} as PatientRegistrationPayload)
    expect(result.hn).toBe('RP-000001')
    expect(fetch).toHaveBeenCalledWith('http://127.0.0.1:8080/api/patients', expect.objectContaining({ method: 'POST' }))
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
      text: () => Promise.resolve(JSON.stringify({ id: 1, hn: 'RP-000001', fullName: 'สมชาย', phone: '0812345678', active: true })),
    }))
    await registerPatient({} as PatientRegistrationPayload)
    const [, init] = vi.mocked(fetch).mock.calls[0]!
    expect(new Headers(init?.headers).get('Authorization')).toBe('Bearer jwt-test')
    localStorage.removeItem('authToken')
  })
})
