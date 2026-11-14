import { api } from './http'

export type Staff = {
  id: number
  branchCode?: string
  branchName?: string
  position?: string
  userId?: number
  username?: string
  role?: 'MANAGER' | 'THERAPIST'
  employeeCode: string
  fullName: string
  phone?: string
  active: boolean
}

export type Therapist = {
  id: number
  code: string
  fullName: string
  branchCode: string
}

export const staffApi = {
  list: () => api.get<Staff[]>('/api/staff'),
  get: (id: number) => api.get<Staff>(`/api/staff/${id}`),
  create: (body: { userId: number; employeeCode: string; fullName: string; phone?: string }) =>
    api.post<Staff>('/api/staff', body),
  update: (
    id: number,
    body: {
      userId: number
      employeeCode: string
      fullName: string
      phone?: string
    },
  ) => api.put<Staff>(`/api/staff/${id}`, body),
  deactivate: (id: number) => api.delete(`/api/staff/${id}`),
}
