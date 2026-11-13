import { describe, expect, it } from 'vitest'
import { GOOGLE_CANCEL_REASON, hiddenFromGoogle } from '../googleCancel'

describe('hiddenFromGoogle', () => {
  it('drops appointments cancelled because their Google event was deleted', () => {
    type Item = { id: number; cancelReason?: string }
    const kept: Item = { id: 1 }
    const cancelledByStaff: Item = { id: 2, cancelReason: 'ผู้ป่วยขอยกเลิก' }
    const deletedInGoogle: Item = { id: 3, cancelReason: GOOGLE_CANCEL_REASON }
    expect(hiddenFromGoogle([kept, cancelledByStaff, deletedInGoogle])).toEqual([kept, cancelledByStaff])
  })
})
