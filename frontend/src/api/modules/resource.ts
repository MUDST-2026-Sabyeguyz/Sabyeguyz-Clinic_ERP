import { api } from './http'

export type ClinicResource = {
  id: number
  branchCode: string
  branchName: string
  code: string
  name: string
  type: string
  status: string
  active: boolean
}

export const resourceApi = {
  list: () => api.get<ClinicResource[]>('/api/resources'),
  create: (body: object) => api.post<ClinicResource>('/api/resources', body),
  update: (id: number, body: object) => api.put<ClinicResource>(`/api/resources/${id}`, body),
  deactivate: (id: number) => api.delete(`/api/resources/${id}`),
  activate: (id: number) => api.patch<ClinicResource>(`/api/resources/${id}/activate`, {}),
}

export type MasterData = {
  id: number
  category: string
  code: string
  name: string
  active: boolean
}

export const masterDataApi = {
  list: () => api.get<MasterData[]>('/api/master-data'),
  create: (body: object) => api.post<MasterData>('/api/master-data', body),
  update: (id: number, body: object) => api.put<MasterData>(`/api/master-data/${id}`, body),
  deactivate: (id: number) => api.delete(`/api/master-data/${id}`),
  activate: (id: number) => api.patch<MasterData>(`/api/master-data/${id}/activate`, {}),
}
