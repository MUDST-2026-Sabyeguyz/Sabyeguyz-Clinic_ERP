import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { setManagerBranches } from '@/features/staff/data/managerBranch'
import View from '../views/CommissionCompensationSettingsView.vue'

const api = vi.hoisted(() => ({ fees: vi.fn(), backfill: vi.fn(), create: vi.fn() }))
vi.mock('@/api/client', () => ({
  commissionApi: {
    treatmentFees: api.fees,
    backfillSingleVisitFees: api.backfill,
    createTreatmentFee: api.create,
  },
}))
const open = () => mount(View, { global: { stubs: { StaffHeader: true, CommissionTabs: true, Teleport: true } } })

beforeEach(() => {
  vi.resetAllMocks()
  setManagerBranches([{ code: '01', name: 'Synthetic branch' }])
  api.fees.mockResolvedValue([])
})

describe('merged compensation settings', () => {
  it('keeps the modern create drawer alongside develop backfill and guards duplicate pending submissions', async () => {
    let release!: (value: { recorded: number }) => void
    api.backfill.mockReturnValue(new Promise((resolve) => { release = resolve }))
    const view = open()
    await flushPromises()
    const add = view.findAll('button').find((button) => button.text().includes('เพิ่มกฎค่ามือ'))!
    await add.trigger('click')
    expect(view.find('[role="dialog"]').exists()).toBe(true)
    const form = view.get('form.backfill-controls')
    const dates = form.findAll('input')
    await dates[0]!.setValue('2026-10-01')
    await dates[1]!.setValue('2026-10-31')
    await form.trigger('submit')
    await form.trigger('submit')
    expect(api.backfill).toHaveBeenCalledTimes(1)
    expect(api.backfill).toHaveBeenCalledWith('2026-10-01', '2026-10-31')
    expect(form.get('button').attributes('disabled')).toBeDefined()
    release({ recorded: 3 })
    await flushPromises()
    expect(view.text()).toContain('เพิ่ม 3 รายการ')
    expect(form.get('button').attributes('disabled')).toBeUndefined()
  })

  it('rejects a reversed range without calling the API and shows a real request failure', async () => {
    const view = open()
    await flushPromises()
    const form = view.get('form.backfill-controls')
    const dates = form.findAll('input')
    await dates[0]!.setValue('2026-11-10')
    await dates[1]!.setValue('2026-11-01')
    await form.trigger('submit')
    expect(api.backfill).not.toHaveBeenCalled()
    expect(view.text()).toContain('ช่วงวันที่ให้ถูกต้อง')
    await dates[1]!.setValue('2026-11-30')
    api.backfill.mockRejectedValue(new Error('Synthetic failure'))
    await form.trigger('submit')
    await flushPromises()
    expect(view.text()).toContain('Synthetic failure')
    expect(form.get('button').attributes('disabled')).toBeUndefined()
  })
})
