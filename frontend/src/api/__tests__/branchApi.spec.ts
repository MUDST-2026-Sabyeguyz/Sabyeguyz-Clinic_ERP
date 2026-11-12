import { afterEach, describe, expect, it, vi } from 'vitest'
import { branchApi } from '../client'

afterEach(() => vi.unstubAllGlobals())

describe('branchApi.listActive', () => {
  it('leaves out a closed branch, so no other screen offers it, while list still has every branch', async () => {
    const branches = [
      { id: 1, branchCode: '01', name: 'Open', active: true },
      { id: 2, branchCode: '02', name: 'Closed', active: false },
    ]
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, text: async () => JSON.stringify(branches) })))
    expect((await branchApi.listActive()).map((branch) => branch.name)).toEqual(['Open'])
    expect(await branchApi.list()).toHaveLength(2)
  })
})
