import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AGENT_URL, agentLocal } from './agentLocal'

const reply = (status: number, body: unknown) =>
  Promise.resolve(new Response(typeof body === 'string' ? body : JSON.stringify(body), { status }))

const paired = { paired: true, workstationName: 'เคาน์เตอร์ 1', branchCode: '01', workstationId: 7, version: '1.0.0' }

describe('agentLocal', () => {
  const fetchMock = vi.fn<typeof fetch>()
  beforeEach(() => {
    fetchMock.mockReset()
    vi.stubGlobal('fetch', fetchMock)
  })
  afterEach(() => vi.unstubAllGlobals())

  it('asks the agent on this PC for its state', async () => {
    fetchMock.mockReturnValue(reply(200, paired))
    expect(await agentLocal.status()).toEqual(paired)
    expect(fetchMock.mock.calls[0]![0]).toBe(`${AGENT_URL}/status`)
  })

  it('says there is no agent when the request fails, is refused, or answers nonsense', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'))
    expect(await agentLocal.status()).toBeNull()
    fetchMock.mockReturnValue(reply(403, { error: 'ปฏิเสธ' }))
    expect(await agentLocal.status()).toBeNull()
    fetchMock.mockReturnValue(reply(200, '<html>not an agent</html>'))
    expect(await agentLocal.status()).toBeNull()
    fetchMock.mockReturnValue(reply(200, { hello: 'world' }))
    expect(await agentLocal.status()).toBeNull()
  })

  it('gives up on an agent that never answers', async () => {
    vi.useFakeTimers()
    try {
      fetchMock.mockImplementation(
        (_url, init) =>
          new Promise<Response>((_resolve, reject) =>
            init?.signal?.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError'))),
          ),
      )
      const pending = agentLocal.status(1000)
      await vi.advanceTimersByTimeAsync(1000)
      expect(await pending).toBeNull()
    } finally {
      vi.useRealTimers()
    }
  })

  it('sends the pairing code and returns the new state', async () => {
    fetchMock.mockReturnValue(reply(200, paired))
    const result = await agentLocal.pair('ABCD2345')
    expect(result).toEqual({ ok: true, status: paired })
    const [url, init] = fetchMock.mock.calls[0]! as unknown as [string, RequestInit]
    expect(url).toBe(`${AGENT_URL}/pair`)
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body as string)).toEqual({ code: 'ABCD2345' })
  })

  it('explains a wrong or expired code with the agent’s own words', async () => {
    fetchMock.mockReturnValue(reply(400, { error: 'จับคู่ไม่สำเร็จ: รหัสไม่ถูกต้องหรือหมดอายุ' }))
    expect(await agentLocal.pair('WRONG123')).toEqual({ ok: false, message: 'จับคู่ไม่สำเร็จ: รหัสไม่ถูกต้องหรือหมดอายุ' })
  })

  it('explains when the agent does not allow this site', async () => {
    fetchMock.mockReturnValue(reply(403, { error: 'ปฏิเสธ' }))
    const result = await agentLocal.pair('ABCD2345')
    expect(result).toEqual({ ok: false, message: expect.stringContaining('ไม่อนุญาต') })
  })

  it('explains when the agent cannot be reached or answers nonsense', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'))
    const unreachable = await agentLocal.pair('ABCD2345')
    expect(unreachable).toEqual({ ok: false, message: expect.stringContaining('ติดต่อโปรแกรมตัวกลาง') })
    fetchMock.mockReturnValue(reply(500, 'oops'))
    const broken = await agentLocal.pair('ABCD2345')
    expect(broken).toEqual({ ok: false, message: expect.stringContaining('จับคู่ไม่สำเร็จ') })
  })
})
