export const EMPTY_COMPENSATION_COPY = 'ยังไม่มีข้อมูลค่าตอบแทนสำหรับบัญชีนี้'

export const THERAPIST_COMPENSATION_TABS = [
  { key: 'overview', label: 'ภาพรวม' },
  { key: 'commission', label: 'ค่าคอมมิชชัน' },
  { key: 'treatment', label: 'ค่ามือ' },
  { key: 'payments', label: 'ประวัติการจ่าย' },
] as const

export type TherapistCompensationTab = (typeof THERAPIST_COMPENSATION_TABS)[number]['key']

export function resolveTherapistCompensationTab(value: unknown): TherapistCompensationTab {
  return THERAPIST_COMPENSATION_TABS.some((tab) => tab.key === value)
    ? (value as TherapistCompensationTab)
    : 'overview'
}
