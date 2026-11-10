import { describe, expect, it } from 'vitest'
import { GOOGLE_CANCEL_REASON, hiddenFromGoogle } from '../googleCancel'

describe('hiddenFromGoogle', () => {
  it('drops appointments cancelled because their Google event was deleted', () => {
    const kept = { id: 1 }
    const cancelledByStaff = { id: 2, cancelReason: 'ผู้ป่วยขอยกเลิก' }
    const deletedInGoogle = { id: 3, cancelReason: GOOGLE_CANCEL_REASON }
    expect(hiddenFromGoogle([kept, cancelledByStaff, deletedInGoogle])).toEqual([kept, cancelledByStaff])
  })
})
