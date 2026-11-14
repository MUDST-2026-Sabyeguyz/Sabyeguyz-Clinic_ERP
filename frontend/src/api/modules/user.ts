import { api } from './http'

export type ManagedUser = {
  id: number
  username: string
  email: string
  role: 'MANAGER' | 'THERAPIST'
  employeeCode: string
  fullName: string
  phone?: string
  branchCode?: string
  branchName?: string
  active: boolean
}

// สิทธิ์เฉพาะผู้ใช้ที่เพิ่มหรือยกเว้นจาก role หลัก
export type UserPermissionOverride = {
  permissionId: number
  permissionName: string
  allowed: boolean
  updatedBy: number
  updatedAt: string
}

export type PermissionCatalogItem = {
  id: number
  permissionName: string
  moduleName: string | null
}

export const userManagementApi = {
  list: () => api.get<ManagedUser[]>('/api/users'),
  permissionCatalog: () => api.get<PermissionCatalogItem[]>('/api/permissions'),
  permissionOverrides: (userId: number) =>
    api.get<UserPermissionOverride[]>(`/api/users/${userId}/permissions`),
  setPermissionOverride: (userId: number, permissionId: number, allowed: boolean) =>
    api.put<UserPermissionOverride>(`/api/users/${userId}/permissions/${permissionId}`, { allowed }),
  resetPermissionOverride: (userId: number, permissionId: number) =>
    api.delete(`/api/users/${userId}/permissions/${permissionId}`),
  create: (body: {
    username: string
    email: string
    password: string
    role: 'MANAGER' | 'THERAPIST'
    employeeCode: string
    fullName: string
    phone?: string
    branchCode?: string
  }) => api.post<ManagedUser>('/api/users', body),
  update: (
    id: number,
    body: {
      username: string
      email: string
      password?: string
      role: 'MANAGER' | 'THERAPIST'
      employeeCode: string
      fullName: string
      phone?: string
      branchCode?: string
    },
  ) => api.put<ManagedUser>(`/api/users/${id}`, body),
  deactivate: (id: number) => api.delete(`/api/users/${id}`),
  activate: (id: number) => api.patch<ManagedUser>(`/api/users/${id}/activate`, {}),
}
