import { afterEach, describe, expect, it, vi } from 'vitest'
import { commissionApi, userManagementApi } from '../client'

describe('commission API main contract', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('uses YYYY-MM and branchCode for branch closings and preserves array responses', async () => {
    const rows = [{ id: 3, status: 'OPEN' }]
    const fetchMock = vi.fn(async () => new Response(JSON.stringify(rows), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(commissionApi.closeMonth('2026-10', '01')).resolves.toEqual(rows)
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/commission/closings/2026-10?branchCode=01',
      expect.objectContaining({ method: 'POST', body: '{}' }),
    )
    await commissionApi.branchClosing('2026-10', '01')
    expect(fetchMock).toHaveBeenLastCalledWith(
      '/api/commission/branch-closings?month=2026-10&branchCode=01',
      expect.any(Object),
    )
    await commissionApi.preparePayments('2026-10', '01')
    expect(fetchMock).toHaveBeenLastCalledWith(
      '/api/commission/payments/prepare?month=2026-10&branchCode=01',
      expect.objectContaining({ method: 'POST' }),
    )
    await commissionApi.closePayments('2026-10', '01')
    expect(fetchMock).toHaveBeenLastCalledWith(
      '/api/commission/payments/close?month=2026-10&branchCode=01',
      expect.objectContaining({ method: 'POST' }),
    )
  })

  it('sends the paid date and reference and includes the required reopen reason', async () => {
    const fetchMock = vi.fn(async () => new Response('{}', { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    await commissionApi.pay(12, '2026-10-30', 'TRANSFER-123')
    expect(fetchMock).toHaveBeenLastCalledWith(
      '/api/commission/payments/12/pay',
      expect.objectContaining({ body: JSON.stringify({ paidDate: '2026-10-30', reference: 'TRANSFER-123' }) }),
    )

    await commissionApi.reopenClosing(15, 'ตรวจทานรายการ')
    expect(fetchMock).toHaveBeenLastCalledWith(
      '/api/commission/closings/15/reopen',
      expect.objectContaining({ body: JSON.stringify({ reason: 'ตรวจทานรายการ' }) }),
    )
  })

  it('creates a treatment-fee rule with a branch and percentage entered as whole percent', async () => {
    const fetchMock = vi.fn(async () => new Response('{}', { status: 201 }))
    vi.stubGlobal('fetch', fetchMock)

    await commissionApi.createTreatmentFee({
      name: 'ค่ามือมาตรฐาน',
      feeType: 'Percentage',
      feeValue: 10,
      effectiveFrom: '2026-12-01',
      branchCode: '01',
    })
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/commission/treatment-fees',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({
          name: 'ค่ามือมาตรฐาน',
          feeType: 'Percentage',
          feeValue: 10,
          effectiveFrom: '2026-12-01',
          branchCode: '01',
        }),
      }),
    )
  })

  it('starts a scheme version through the version endpoint with branch context and no client version', async () => {
    const fetchMock = vi.fn(async () => new Response('{}', { status: 201 }))
    vi.stubGlobal('fetch', fetchMock)

    await commissionApi.createSchemeVersion(4, {
      name: 'Tier ใหม่',
      effectiveFrom: '2026-12-01',
      branchCode: '01',
      tiers: [{ minimum: 0, maximum: null, rate: 0.07, order: 1 }],
    })
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/commission/schemes/4/versions',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({
          name: 'Tier ใหม่',
          effectiveFrom: '2026-12-01',
          branchCode: '01',
          tiers: [{ minimum: 0, maximum: null, rate: 0.07, order: 1 }],
        }),
      }),
    )
  })

  it('reads, updates, and resets a real per-user permission override', async () => {
    const fetchMock = vi.fn(async () => new Response('{}', { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    await userManagementApi.permissionOverrides(31)
    expect(fetchMock).toHaveBeenLastCalledWith('/api/users/31/permissions', expect.any(Object))

    const catalog = [{ id: 8, permissionName: 'SALE_CANCEL', moduleName: 'Sale' }]
    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify(catalog), { status: 200 }))
    await expect(userManagementApi.permissionCatalog()).resolves.toEqual(catalog)
    expect(fetchMock).toHaveBeenLastCalledWith('/api/permissions', expect.any(Object))

    await userManagementApi.setPermissionOverride(31, 8, false)
    expect(fetchMock).toHaveBeenLastCalledWith(
      '/api/users/31/permissions/8',
      expect.objectContaining({ method: 'PUT', body: JSON.stringify({ allowed: false }) }),
    )

    await userManagementApi.resetPermissionOverride(31, 8)
    expect(fetchMock).toHaveBeenLastCalledWith(
      '/api/users/31/permissions/8',
      expect.objectContaining({ method: 'DELETE' }),
    )
  })
})
