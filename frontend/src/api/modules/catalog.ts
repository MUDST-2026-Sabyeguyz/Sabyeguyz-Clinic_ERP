import { api } from './http'
import { type Therapist } from './staff'

export type ServiceOption = {
  id: number
  code: string
  name: string
  durationMinutes: number
  price: number
}

export type Course = {
  id: number
  code: string
  name: string
  price: number
  sessions: number
  bonusSessions: number
  validityDays: number
  validityValue?: number
  validityUnit?: 'Day' | 'Month' | 'Year'
  defaultDurationMinutes?: number | null
  active: boolean
}

export const catalogApi = {
  courses: () => api.get<Course[]>('/api/catalog/courses'),
  services: () => api.get<ServiceOption[]>('/api/catalog/services'),
  therapists: () => api.get<Therapist[]>('/api/catalog/therapists'),
  lookupServices: () =>
    api.get<
      Array<{
        id: number
        code: string
        name: string
        defaultDurationMinutes: number
        active: boolean
      }>
    >('/api/lookups/services'),
  lookupTherapists: (branchCode?: string) =>
    api.get<
      Array<{
        id: number
        code: string
        fullName: string
        branchCode: string
        active: boolean
      }>
    >(
      `/api/lookups/therapists${branchCode ? `?branchCode=${encodeURIComponent(branchCode)}` : ''}`,
    ),
}

export type ManagedService = {
  id: number
  code: string
  name: string
  defaultDurationMinutes: number
  price: number
  active: boolean
}

export type ManagedServiceRequest = Omit<
  ManagedService,
  'id' | 'active' | 'defaultDurationMinutes'
> & {
  defaultDurationMinutes?: number | null
}

export const serviceManagementApi = {
  list: () => api.get<ManagedService[]>('/api/services'),
  create: (body: ManagedServiceRequest) => api.post<ManagedService>('/api/services', body),
  update: (id: number, body: ManagedServiceRequest) =>
    api.put<ManagedService>(`/api/services/${id}`, body),
  deactivate: (id: number) => api.delete(`/api/services/${id}`),
  activate: (id: number) => api.patch<ManagedService>(`/api/services/${id}/activate`, {}),
  changeType: (id: number, type: 'SingleVisit' | 'Course') =>
    api.post<ManagedService>(`/api/services/${id}/type`, { type }),
}
