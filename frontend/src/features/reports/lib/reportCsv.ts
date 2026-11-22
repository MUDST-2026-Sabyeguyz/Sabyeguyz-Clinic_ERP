export function buildReportCsv(input: {
  title: string
  filters: Record<string, string>
  exportedAt: string
  headers: string[]
  rows: string[][]
  total: string[] | null
}): string {
  if (!input.rows.length) throw new Error('ไม่พบข้อมูลตามเงื่อนไขที่เลือก')
  const cell = (value: string) => {
    const safe = /^[=+\-@]/.test(value) ? `'${value}` : value
    return /[",\r\n]/.test(safe) ? `"${safe.replace(/"/g, '""')}"` : safe
  }
  const meta = [
    ['รายงาน', input.title],
    ['เงื่อนไข', Object.entries(input.filters).map(([name, value]) => `${name}: ${value || 'ทั้งหมด'}`).join(' | ')],
    ['วันที่ส่งออก', input.exportedAt],
    [],
    input.headers,
    ...input.rows,
    ...(input.total ? [input.total] : []),
  ]
  return `\uFEFF${meta.map((row) => row.map((value) => cell(String(value ?? ''))).join(',')).join('\r\n')}`
}
