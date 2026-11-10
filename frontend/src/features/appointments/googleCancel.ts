// An appointment whose Google Calendar event was deleted is cancelled by the sync (reason below). That frees its time for a
// new booking, and it is not shown in the appointment list or the calendar.
export const GOOGLE_CANCEL_REASON = 'ยกเลิกจาก Google Calendar'
export const hiddenFromGoogle = <T extends { cancelReason?: string }>(items: T[]) =>
  items.filter((item) => item.cancelReason !== GOOGLE_CANCEL_REASON)
