import { appointmentApi, getToken, type Appointment, type AppointmentPage } from '@/api/client'

type AppointmentFilters = Omit<
  NonNullable<Parameters<typeof appointmentApi.search>[0]>,
  'page' | 'size'
>

// Coalesce identical in-flight pages without retaining completed history.
const pendingPages = new Map<string, Promise<AppointmentPage>>()
function readPage(filters: AppointmentFilters, page: number) {
  const key = JSON.stringify([getToken(), filters, page])
  const existing = pendingPages.get(key)
  if (existing) return existing
  const request = appointmentApi.search({ ...filters, page, size: 100 }).finally(() => {
    if (pendingPages.get(key) === request) pendingPages.delete(key)
  })
  pendingPages.set(key, request)
  return request
}

// Service-type filtering is local: collect the selected date range before paginating it.
export async function readAppointmentRange(
  filters: AppointmentFilters,
  isCurrent: () => boolean = () => true,
): Promise<Appointment[] | null> {
  const first = await readPage(filters, 0)
  if (!isCurrent()) return null
  const items = [...first.items]
  for (let page = 1; page < first.totalPages; page += 4) {
    if (!isCurrent()) return null
    const pages = await Promise.all(
      Array.from({ length: Math.min(4, first.totalPages - page) }, (_, offset) =>
        readPage(filters, page + offset),
      ),
    )
    if (!isCurrent()) return null
    pages.forEach((result) => items.push(...result.items))
  }
  return items
}
