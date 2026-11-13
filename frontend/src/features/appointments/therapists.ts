import { catalogApi, staffApi } from '@/api/client'

// The catalog endpoint includes all active staff, so use the staff position to
// avoid presenting managers as therapists in appointment controls.
export async function loadAppointmentTherapists() {
  const [catalog, staff] = await Promise.all([catalogApi.therapists(), staffApi.list()])
  const ids = new Set(
    staff
      .filter(
        (person) =>
          (person.position ?? person.role ?? '').toLowerCase() === 'therapist' && person.active,
      )
      .map((person) => person.id),
  )
  return catalog.filter((person) => ids.has(person.id))
}
