import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, type IdCardReadView, type IdCardWorkstation } from '@/api/client'
import type { LocalAgentStatus } from './agentLocal'
import { useIdCardReader } from './useIdCardReader'

// no agent on this PC unless a test says so (these tests run on fake timers, so nothing may wait on a real request)
vi.mock('./agentLocal', () => ({ agentLocal: { status: vi.fn<() => Promise<null>>().mockResolvedValue(null), pair: vi.fn<() => void>() } }))

const agent = (over: Partial<{ paired: boolean; workstationId: number | null }> = {}) => ({
  status: vi.fn<() => Promise<LocalAgentStatus | null>>().mockResolvedValue({
    paired: true,
    workstationName: 'เคาน์เตอร์ 2',
    branchCode: '01',
    workstationId: 2,
    version: '1.0.0',
    ...over,
  }),
})

const ws =(over: Partial<IdCardWorkstation> = {}): IdCardWorkstation => ({
  id: 1,
  name: 'เคาน์เตอร์ 1',
  branchCode: '01',
  online: true,
  status: 'Active',
  ...over,
})

function fakeApi(results: IdCardReadView[], workstations = [ws()]) {
  const queue = [...results]
  return {
    workstations: vi.fn().mockResolvedValue(workstations),
    start: vi.fn().mockResolvedValue({ id: 'r1', expiresAt: '2026-11-10T10:01:00Z' }),
    result: vi.fn().mockImplementation(async () => queue.shift() ?? { status: 'PENDING' }),
  }
}

describe('useIdCardReader', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    localStorage.clear()
  })
  afterEach(() => vi.useRealTimers())

  it('selects the only online reader on its own', async () => {
    const reader = useIdCardReader({ api: fakeApi([]) as never })
    await reader.load('01')
    expect(reader.selectedId.value).toBe(1)
  })

  it('waits for the card, then says it is reading, then resolves with the result', async () => {
    const api = fakeApi([
      { status: 'PENDING' },
      { status: 'READING' },
      { status: 'DONE', lookup: { exists: false, patient: null }, card: { nationalId: '1101700005119' } as never },
    ])
    const reader = useIdCardReader({ api: api as never, intervalMs: 700 })
    await reader.load('01')
    const done = reader.read()
    expect(reader.state.value).toBe('waiting')
    await vi.advanceTimersByTimeAsync(800)
    expect(reader.state.value).toBe('waiting')
    await vi.advanceTimersByTimeAsync(700)
    expect(reader.state.value).toBe('reading')
    await vi.advanceTimersByTimeAsync(700)
    const view = await done
    expect(view?.card?.nationalId).toBe('1101700005119')
    expect(view?.lookup?.exists).toBe(false)
    expect(reader.state.value).toBe('done')
    expect(api.start).toHaveBeenCalledWith(1, 'REGISTER')
  })

  it('returns the patient when the card belongs to someone already registered', async () => {
    const api = fakeApi([
      { status: 'DONE', lookup: { exists: true, patient: { id: 5, hn: 'HN0005', fullName: 'สมชาย ใจดี' } } },
    ])
    const reader = useIdCardReader({ api: api as never })
    await reader.load('01')
    const done = reader.read()
    await vi.advanceTimersByTimeAsync(800)
    const view = await done
    expect(view?.lookup?.patient?.id).toBe(5)
    expect(view?.card).toBeUndefined()
  })

  it('reports a failed read with a Thai message', async () => {
    const api = fakeApi([{ status: 'FAILED', outcome: 'NO_CARD', message: 'ไม่พบบัตร ใส่บัตรแล้วลองใหม่' }])
    const reader = useIdCardReader({ api: api as never })
    await reader.load('01')
    const done = reader.read()
    await vi.advanceTimersByTimeAsync(800)
    expect(await done).toBeNull()
    expect(reader.state.value).toBe('failed')
    expect(reader.message.value).toBe('ไม่พบบัตร ใส่บัตรแล้วลองใหม่')
  })

  it('says so when the counter program is offline (409) or too many reads (429)', async () => {
    const api = fakeApi([])
    api.start.mockRejectedValueOnce(new ApiError('offline', 409))
    const reader = useIdCardReader({ api: api as never })
    await reader.load('01')
    expect(await reader.read()).toBeNull()
    expect(reader.message.value).toContain('ออฟไลน์')

    api.start.mockRejectedValueOnce(new ApiError('slow down', 429))
    expect(await reader.read()).toBeNull()
    expect(reader.message.value).toContain('บ่อยเกินไป')
  })

  it('gives up after the timeout', async () => {
    const reader = useIdCardReader({ api: fakeApi([]) as never, intervalMs: 700, timeoutMs: 3000 })
    await reader.load('01')
    const done = reader.read()
    await vi.advanceTimersByTimeAsync(4000)
    expect(await done).toBeNull()
    expect(reader.state.value).toBe('failed')
    expect(reader.message.value).toContain('หมดเวลา')
  })

  it('stops polling when cancelled (the dialog was closed)', async () => {
    const api = fakeApi([])
    const reader = useIdCardReader({ api: api as never, intervalMs: 700 })
    await reader.load('01')
    const done = reader.read()
    await vi.advanceTimersByTimeAsync(800)
    reader.cancel()
    await vi.advanceTimersByTimeAsync(5000)
    expect(await done).toBeNull()
    expect(reader.state.value).toBe('idle')
    const calls = api.result.mock.calls.length
    await vi.advanceTimersByTimeAsync(5000)
    expect(api.result.mock.calls.length).toBe(calls)
  })

  it('needs a reader to be selected first', async () => {
    const reader = useIdCardReader({ api: fakeApi([], []) as never })
    await reader.load('01')
    expect(await reader.read()).toBeNull()
    expect(reader.message.value).toContain('เครื่องอ่านบัตร')
  })

  it('remembers the chosen reader in the browser', async () => {
    const reader = useIdCardReader({
      api: fakeApi([], [ws(), ws({ id: 2, name: 'เคาน์เตอร์ 2' })]) as never,
    })
    await reader.load('01')
    expect(reader.selectedId.value).toBeNull()
    reader.select(2)
    expect(localStorage.getItem('idCardWorkstationId')).toBe('2')
    const again = useIdCardReader({
      api: fakeApi([], [ws(), ws({ id: 2, name: 'เคาน์เตอร์ 2' })]) as never,
    })
    await again.load('01')
    expect(again.selectedId.value).toBe(2)
  })

  describe('with the agent installed on this PC', () => {
    const two = () => fakeApi([], [ws(), ws({ id: 2, name: 'เคาน์เตอร์ 2' })]) as never

    it('selects the reader the agent is paired with, even among several', async () => {
      const reader = useIdCardReader({ api: two(), local: agent() })
      await reader.load('01')
      expect(reader.selectedId.value).toBe(2)
    })

    it('prefers it over a reader remembered from an earlier visit', async () => {
      localStorage.setItem('idCardWorkstationId', '1')
      const reader = useIdCardReader({ api: two(), local: agent() })
      await reader.load('01')
      expect(reader.selectedId.value).toBe(2)
    })

    it('does not use an agent that is not paired', async () => {
      const reader = useIdCardReader({ api: two(), local: agent({ paired: false, workstationId: null }) })
      await reader.load('01')
      expect(reader.selectedId.value).toBeNull()
    })

    it('ignores the agent’s reader when it was cancelled, and falls back to the only online one', async () => {
      const reader = useIdCardReader({
        api: fakeApi([], [ws(), ws({ id: 2, status: 'Revoked', online: false })]) as never,
        local: agent(),
      })
      await reader.load('01')
      expect(reader.selectedId.value).toBe(1)
    })

    it('carries on as before when the agent does not answer', async () => {
      const reader = useIdCardReader({
        api: two(),
        local: { status: vi.fn<() => Promise<LocalAgentStatus | null>>().mockRejectedValue(new Error('refused')) },
      })
      await reader.load('01')
      expect(reader.workstations.value).toHaveLength(2)
      expect(reader.selectedId.value).toBeNull()
    })

    it('still lists nothing, without failing, when the backend list cannot be loaded', async () => {
      const api = { ...fakeApi([]), workstations: vi.fn<() => Promise<unknown>>().mockRejectedValue(new Error('offline')) }
      const reader = useIdCardReader({ api: api as never, local: agent() })
      await reader.load('01')
      expect(reader.workstations.value).toEqual([])
      expect(reader.selectedId.value).toBeNull()
    })
  })
})
