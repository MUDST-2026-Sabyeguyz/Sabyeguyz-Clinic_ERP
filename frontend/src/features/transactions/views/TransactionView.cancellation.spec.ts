import { describe, expect, it } from 'vitest'
import source from './TransactionView.vue?raw'

describe('transaction cancellation review', () => {
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

  it('keeps a typed fallback for a cancellation reason outside the presets', () => {
    expect(source).toContain("reasonType === 'อื่น ๆ'")
    expect(source).toContain('รายละเอียดเหตุผล *')
  })
})
