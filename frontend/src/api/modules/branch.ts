import { api } from './http'

export type Branch = {
  id: number
  code: string
  numericCode: string
  branchCode: string
  name: string
  address?: string
  phone?: string
  email?: string
  licenseNumber?: string
  licenseNo?: string | null
  taxId?: string
  bookingOpenTime?: string | null
  bookingCloseTime?: string | null
  defaultAppointmentMinutes?: number | null
  active: boolean
}

export type BranchRequest = {
  branchCode?: string
  name: string
  address?: string
  phone?: string
  email?: string
  licenseNo?: string | null
  bookingOpenTime?: string | null
  bookingCloseTime?: string | null
  defaultAppointmentMinutes?: number | null
}

type BackendBranch = {
  id: number
  branchCode: string
  name: string
  address?: string
  phone?: string
  email?: string | null
  licenseNo?: string | null
  taxId?: string | null
  bookingOpenTime?: string | null
  bookingCloseTime?: string | null
  defaultAppointmentMinutes?: number | null
  active: boolean
}

function normalizeBranch(branch: BackendBranch): Branch {
  return {
    id: branch.id,
    code: branch.branchCode,
    numericCode: branch.branchCode,
    branchCode: branch.branchCode,
    name: branch.name,
    address: branch.address,
    phone: branch.phone,
    email: branch.email ?? undefined,
    licenseNumber: branch.licenseNo ?? undefined,
    licenseNo: branch.licenseNo,
    taxId: branch.taxId ?? undefined,
    bookingOpenTime: branch.bookingOpenTime,
    bookingCloseTime: branch.bookingCloseTime,
    defaultAppointmentMinutes: branch.defaultAppointmentMinutes,
    active: branch.active,
  }
}

export const branchApi = {
  list: () => api.get<BackendBranch[]>('/api/branches').then((items) => items.map(normalizeBranch)),
  listActive: async () => (await branchApi.list()).filter((branch) => branch.active !== false),
  get: (id: number) => api.get<BackendBranch>(`/api/branches/${id}`).then(normalizeBranch),
  create: (body: BranchRequest | { code: string; numericCode: string; name: string }) =>
    api
      .post<BackendBranch>('/api/branches', {
        ...body,
        branchCode: 'code' in body ? body.code || body.numericCode : body.branchCode,
        name: body.name,
      })
      .then(normalizeBranch),
  update: (id: number, body: BranchRequest | { code: string; numericCode: string; name: string }) =>
    api
      .put<BackendBranch>(`/api/branches/${id}`, {
        ...body,
        branchCode: 'code' in body ? body.code || body.numericCode : body.branchCode,
        name: body.name,
      })
      .then(normalizeBranch),
  activate: (id: number) => api.patch<BackendBranch>(`/api/branches/${id}/activate`, {}),
  deactivate: (id: number) => api.delete(`/api/branches/${id}`),
}
