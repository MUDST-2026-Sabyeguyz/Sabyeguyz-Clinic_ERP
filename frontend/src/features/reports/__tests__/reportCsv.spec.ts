import { describe, expect, it } from 'vitest'
import { buildReportCsv } from '../lib/reportCsv'

describe('report CSV export', () => {
  it('includes report metadata, every filtered row, totals and escapes cells', () => {
    const csv = buildReportCsv({
      title: 'รายงานยอดรับชำระ',
      filters: { from: '2026-11-01', to: '2026-11-30', branch: '01' },
      exportedAt: '2026-11-12 10:00',
      headers: ['ผู้ป่วย', 'ยอดรับ'],
      rows: [['=formula', '1,200.00'], ['ผู้ป่วย "ทดสอบ"', '400.00']],
      total: ['รวมทั้งหมด', '1,600.00'],
    })
    expect(csv.startsWith('\uFEFF')).toBe(true)
    expect(csv).toContain('รายงานยอดรับชำระ')
    expect(csv).toContain('from: 2026-11-01 | to: 2026-11-30 | branch: 01')
    expect(csv).toContain("'=formula")
    expect(csv).toContain('"ผู้ป่วย ""ทดสอบ"""')
    expect(csv).toContain('รวมทั้งหมด,"1,600.00"')
  })

  it('does not create a misleading empty report', () => {
    expect(() => buildReportCsv({
      title: 'รายงาน', filters: {}, exportedAt: 'now', headers: ['คอลัมน์'], rows: [], total: null,
    })).toThrow('ไม่พบข้อมูลตามเงื่อนไขที่เลือก')
  })
})
