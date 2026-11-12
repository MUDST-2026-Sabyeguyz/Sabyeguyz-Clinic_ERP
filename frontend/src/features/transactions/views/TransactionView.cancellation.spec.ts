import { describe, expect, it } from 'vitest'
import source from './TransactionView.vue?raw'

describe('transaction cancellation review', () => {
  it('orders transaction tabs from newest to oldest by default', () => {
    expect(source).toContain("const sortOrder = ref<'oldest' | 'newest'>('newest')")
    expect(source).toContain("sortOrder === 'newest' ? 'ล่าสุด↓' : 'ล่าสุด↑'")
  })

  it('uses the transaction list heading on the main workspace', () => {
    expect(source).toContain(": 'รายการธุรกรรม'")
  })

  it('keeps cancellation information behind the heading icon', () => {
    expect(source).not.toContain('class="transaction-reason-shortcut"')
    expect(source).toContain('aria-label="ข้อมูลเพิ่มเติมเกี่ยวกับการยกเลิก"')
    expect(source).not.toContain('ข้อมูลผลของการยกเลิก')
    expect(source).toContain('v-if="showCancellationInfo"')
    expect(source).toContain('ระบบจะเก็บรายการเดิมและประวัติการยกเลิกไว้เพื่อตรวจสอบย้อนหลัง')
    expect(source).toContain("submitting ? 'กำลังยกเลิก...' : 'ยืนยัน'")
  })

  it('does not present unavailable audit fields as record values', () => {
    expect(source).toContain('v-if="hasRecordValue(selected.performedBy)"')
    expect(source).toContain('v-if="hasRecordValue(selected.paymentMethod)"')
    expect(source).not.toContain('<small>เบอร์โทรศัพท์ -</small>')
  })

  it('sends a reason code and requires separate detail for every cancellation category', () => {
    expect(source).toContain("{ code: 'WrongEntryOrService'")
    expect(source).toContain("{ code: 'DuplicateEntry'")
    expect(source).toContain("{ code: 'PatientRequested'")
    expect(source).toContain("{ code: 'PaymentFailedOrIncorrectAmount'")
    expect(source).toContain("{ code: 'Other'")
    expect(source).toContain('รายละเอียดเหตุผล *')
    expect(source).toContain('required')
    expect(source).toContain('reasonCode: command.reasonCode')
  })
})
