import { api } from './http'

export type AuditEntry = {
  id: number
  tableName: string
  recordId: string
  action: 'INSERT' | 'UPDATE' | 'DELETE'
  oldValue: string | null
  newValue: string | null
  changedBy: number | null
  changedAt: string
  changedByName: string | null
}

export type AuditPage = {
  items: AuditEntry[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export type AuditFilter = {
  tableName?: string
  recordId?: string
  action?: string
  changedBy?: number
  branchCode?: string
  from?: string
  to?: string
}

export const auditApi = {
  list: (page = 0, size = 50, filter: AuditFilter = {}) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) })
    for (const [key, value] of Object.entries(filter)) {
      if (value !== undefined && value !== '') params.set(key, String(value))
    }
    return api.get<AuditPage>(`/api/audit-logs?${params}`)
  },
  tables: () => api.get<string[]>('/api/audit-logs/tables'),
}
