import { api, request } from './http'

export type CalendarBranchStatus = {
  branchCode: string
  branchName: string
  googleCalendarId: string | null
  waiting: number
  failed: number
}

export type CalendarSyncResult = {
  pushed: number
  removed: number
  failed: number
  movedFromGoogle: number
  cancelledFromGoogle: number
  revertedInGoogle: number
}

export type CalendarStatus = {
  enabled: boolean
  defaultDurationMinutes: number
  lastRunAt: string | null
  lastResult: CalendarSyncResult | null
  branches: CalendarBranchStatus[]
  /** Where the Google key comes from: saved in the web UI, a key file / OAuth values on the server, or none yet. */
  keySource?: 'ui' | 'file' | 'env' | 'none'
  serviceAccountEmail?: string | null
}

export const calendarApi = {
  status: () => api.get<CalendarStatus>('/api/calendar/status'),
  setBranchCalendar: (branchCode: string, googleCalendarId: string) =>
    api.put<CalendarBranchStatus>(`/api/calendar/branches/${encodeURIComponent(branchCode)}`, {
      googleCalendarId,
    }),
  syncNow: () => api.post<CalendarSyncResult>('/api/calendar/sync', {}),
  saveCredentials: (keyJson: string) =>
    api.put<CalendarStatus>('/api/calendar/credentials', { keyJson }),
  removeCredentials: () =>
    request<CalendarStatus>('/api/calendar/credentials', { method: 'DELETE' }),
}
