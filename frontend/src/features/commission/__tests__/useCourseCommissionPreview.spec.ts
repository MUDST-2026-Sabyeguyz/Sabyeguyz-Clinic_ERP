import { flushPromises, mount } from '@vue/test-utils'
import { nextTick, ref } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { CommissionPreview, CommissionPreviewParams } from '@/api/client'
import { useCourseCommissionPreview } from '../useCourseCommissionPreview'

const previewApi = vi.hoisted(() => ({ preview: vi.fn() }))
vi.mock('@/api/client', async (original) => ({
  ...(await original<typeof import('@/api/client')>()),
  commissionApi: { preview: previewApi.preview },
}))

const result = (schemeVersion: number): CommissionPreview => ({
  netPrice: 900,
  commissionRate: 0.07,
  commissionPool: 63,
  commissionPerVisit: 7,
  totalVisits: 9,
  schemeVersion,
  monthlySalesToDate: 0,
})

describe('course commission preview refresh', () => {
  beforeEach(() => previewApi.preview.mockReset())
  afterEach(() => vi.restoreAllMocks())

  it('requests again when course, branch, seller or net price changes and ignores stale responses', async () => {
    let resolveFirst!: (value: CommissionPreview) => void
    const first = new Promise<CommissionPreview>((resolve) => (resolveFirst = resolve))
    previewApi.preview.mockReturnValueOnce(first).mockResolvedValueOnce(result(2))
    const params = ref<CommissionPreviewParams>({
      courseId: 3,
      branchCode: '01',
      sellerStaffId: 8,
      price: 1000,
    })
    let state!: ReturnType<typeof useCourseCommissionPreview>
    const wrapper = mount({
      setup() {
        state = useCourseCommissionPreview(() => params.value)
        return () => null
      },
    })

    await nextTick()
    params.value = { courseId: 4, branchCode: '02', sellerStaffId: 9, price: 900 }
    await nextTick()
    await flushPromises()
    resolveFirst(result(1))
    await flushPromises()

    expect(previewApi.preview).toHaveBeenNthCalledWith(1, {
      courseId: 3,
      branchCode: '01',
      sellerStaffId: 8,
      price: 1000,
    })
    expect(previewApi.preview).toHaveBeenNthCalledWith(2, {
      courseId: 4,
      branchCode: '02',
      sellerStaffId: 9,
      price: 900,
    })
    expect(state.preview.value?.schemeVersion).toBe(2)
    expect(state.preview.value?.commissionRate).toBe(0.07)
    wrapper.unmount()
  })

})
