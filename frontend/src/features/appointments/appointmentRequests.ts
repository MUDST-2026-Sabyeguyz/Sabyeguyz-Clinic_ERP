import { appointmentApi, getToken, type AppointmentProgress } from '@/api/client'
// Coalesce overlapping reads only. Never retain financial status after a completed request.
const pending = new Map<string, Promise<AppointmentProgress>>()
export function readAppointmentProgress(id: number): Promise<AppointmentProgress> {
  const key = String(getToken()) + ':' + id
  const existing = pending.get(key)
  if (existing) return existing
  const request = appointmentApi.progress(id).finally(() => {
    if (pending.get(key) === request) pending.delete(key)
  })
  pending.set(key, request)
  return request
}
