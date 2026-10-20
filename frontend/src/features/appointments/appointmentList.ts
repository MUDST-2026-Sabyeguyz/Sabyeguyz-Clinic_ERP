import { appointmentApi, type Appointment } from '@/api/client'

type AppointmentFilters = Omit<
  NonNullable<Parameters<typeof appointmentApi.search>[0]>,
  'page' | 'size'
>

// Service-type filtering is local: collect the selected date range before paginating it.
export async function readAppointmentRange(
  filters: AppointmentFilters,
  isCurrent: () => boolean = () => true,
): Promise<Appointment[] | null> {
  const first = await appointmentApi.search({ ...filters, page: 0, size: 100 })
  if (!isCurrent()) return null
  const items = [...first.items]
  for (let page = 1; page < first.totalPages; page += 4) {
    if (!isCurrent()) return null
    const pages = await Promise.all(
      Array.from({ length: Math.min(4, first.totalPages - page) }, (_, offset) =>
        appointmentApi.search({ ...filters, page: page + offset, size: 100 }),
      ),
    )
    if (!isCurrent()) return null
    pages.forEach((result) => items.push(...result.items))
  }
  return items
}
