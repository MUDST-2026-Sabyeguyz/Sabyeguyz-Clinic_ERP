// A time that is taken (or cannot be booked) is locked in the time picker: it is disabled and says why.
export function slotLockLabel(reason?: string | null) {
  if (reason?.includes('ผู้ป่วยมีนัด')) return 'ผู้ป่วยมีนัด'
  if (reason?.includes('นัดอื่น')) return 'จองแล้ว'
  if (reason?.includes('เลยเวลา')) return 'เลยเวลาแล้ว'
  return 'ไม่ว่าง'
}
