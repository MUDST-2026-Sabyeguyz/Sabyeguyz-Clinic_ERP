import { beforeEach, describe, expect, it, vi } from 'vitest'
import { readAppointmentRange } from '../appointmentList'
import type { Appointment } from '@/api/client'

const search = vi.hoisted(() => vi.fn())
vi.mock('@/api/client', () => ({ appointmentApi: { search }, getToken: () => 'test-token' }))
const filters = { branchCode: '01', startDate: '2026-10-21', endDate: '2026-10-21' }
const appointment = (id: number) => ({ id }) as Appointment

beforeEach(() => {
  search.mockReset()
})

describe('appointment range loading before service filtering', () => {
  it('collects subsequent server pages with the same branch and date scope', async () => {
    search
      .mockResolvedValueOnce({ items: [appointment(1)], totalPages: 2 })
      .mockResolvedValueOnce({ items: [appointment(2)], totalPages: 2 })
    expect(await readAppointmentRange(filters)).toEqual([appointment(1), appointment(2)])
    expect(search).toHaveBeenNthCalledWith(2, { ...filters, page: 1, size: 100 })
  })

  it('stops fetching pages when a newer filter request replaces it', async () => {
    search.mockResolvedValue({ items: [appointment(1)], totalPages: 8 })
    expect(await readAppointmentRange(filters, () => false)).toBeNull()
    expect(search).toHaveBeenCalledTimes(1)
  })

  it('reports a failed later page rather than presenting incomplete results', async () => {
    search
      .mockResolvedValueOnce({ items: [appointment(1)], totalPages: 2 })
      .mockRejectedValueOnce(new Error('page unavailable'))
    await expect(readAppointmentRange(filters)).rejects.toThrow('page unavailable')
  })

  it('bounds additional page requests to four at a time', async () => {
    let active = 0
    let peak = 0
    search.mockImplementation(async ({ page }: { page: number }) => {
      active++
      peak = Math.max(peak, active)
      await Promise.resolve()
      active--
      return { items: [appointment(page)], totalPages: 10 }
    })
    expect(await readAppointmentRange(filters)).toHaveLength(10)
    expect(search).toHaveBeenCalledTimes(10)
    expect(peak).toBeLessThanOrEqual(4)
  })
})
