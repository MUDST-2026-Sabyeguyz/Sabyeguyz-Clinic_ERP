import { authFetch } from './http'

export interface DashboardSummary {
  from: string; to: string; branchCode: string | null; uniquePatients: number; visits: number
  totalRevenue: number; courseRevenue: number; singleVisitRevenue: number; assessmentRevenue: number
  receiptsIssued: number; receiptsVoided: number
}

const base = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'
export async function getDashboardSummary(from: string, to: string): Promise<DashboardSummary> {
  const response = await authFetch(`${base}/api/dashboard/summary?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`)
  if (!response.ok) throw new Error('ไม่สามารถโหลด Dashboard ได้')
  return response.json()
}
