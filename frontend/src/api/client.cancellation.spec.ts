import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { courseApi, salesApi } from './client'

describe('typed cancellation API requests', () => {
  const fetchMock = vi.fn()
  const request = {
    reasonCode: 'WrongEntryOrService' as const,
    reason: 'เลือกบริการผิดในใบเสร็จ R123',
  }

  beforeEach(() => {
    fetchMock.mockClear()
    fetchMock.mockResolvedValue({ ok: true, status: 200, text: async () => '{}', headers: new Headers() })
    vi.stubGlobal('fetch', fetchMock)
    localStorage.setItem('authToken', 'test-token')
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    localStorage.removeItem('authToken')
  })

  it('sends the selected reason code and separate detail when reversing a course usage', async () => {
    await courseApi.reverse(31, request)
    expect(fetchMock).toHaveBeenCalledOnce()
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/course-movements/31/reverse')
    expect(JSON.parse(String(init.body))).toEqual(request)
  })

  it('sends the selected reason code and separate detail when reversing a sale', async () => {
    await salesApi.reverse(42, request)
    expect(fetchMock).toHaveBeenCalledOnce()
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/payments/42/reverse')
    expect(JSON.parse(String(init.body))).toEqual(request)
  })
})
