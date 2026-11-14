import { api } from './http'

export type DashboardResponse = {
  uniquePatients: number
  patientsByGender: Array<{ gender: string; patients: number }>
  visits: number
  totalRevenue: number
  courseRevenue: number
  singleVisitRevenue: number
  assessmentRevenue: number
  receiptsIssued: number
  receiptsVoided: number
  newCustomers: number
  returningCustomers: number
  salesTotal: number
  cashReceived: number
  courseRenewals: number
  packageUpdates: number
  packageUpdatesScope: 'CLINIC' | string
}

export type AppointmentReport = {
  byStatus: Array<{ status: string; appointments: number }>
  appointments: number
  visits: number
  noShowRate: number
}

export type RevenueReport = {
  totalRevenue: number
  byCategory: Array<{ type: string; amount: number; share: number }>
  dailyByCategory: Array<{
    date: string
    type: string
    amount: number
  }>
}

export type CourseBalanceReport = {
  purchases: number
  rows: Array<{
    hn: string
    patientFullName: string
    branchCode: string
    courseName: string
    purchased: number
    used: number
    remaining: number
    purchasedAt: string
    expiresAt: string
    expired: boolean
  }>
}

export type StaffSalesReport = {
  rows: Array<{ fullName: string; receipts: number; amount: number }>
  unassigned?: { fullName?: string; receipts: number; amount: number }
}

export type TransferRecord = {
  id: number
  sourcePatientId: number
  sourcePatientHn: string
  sourcePatientName: string
  destinationPatientId: number
  destinationPatientHn: string
  destinationPatientName: string
  branchCode: string
  courseId: number
  quantity: number
  reason: string
  createdAt: string
}

export const reportApi = {
  dashboard: (from: string, to: string, branchCode?: string) =>
    api.get<DashboardResponse>(
      `/api/dashboard/summary?${new URLSearchParams({ from, to, ...(branchCode ? { branchCode } : {}) })}`,
    ),
  appointments: (from: string, to: string, branchCode?: string) =>
    api.get<AppointmentReport>(
      `/api/reports/appointments?${new URLSearchParams({ from, to, ...(branchCode ? { branchCode } : {}) })}`,
    ),
  revenue: (from: string, to: string, branchCode?: string) =>
    api.get<RevenueReport>(
      `/api/reports/revenue?${new URLSearchParams({ from, to, ...(branchCode ? { branchCode } : {}) })}`,
    ),
  balances: (from: string, to: string, branchCode?: string, patientId?: number) =>
    api.get<CourseBalanceReport>(
      `/api/reports/course-balances?${new URLSearchParams({ from, to, ...(branchCode ? { branchCode } : {}), ...(patientId ? { patientId: String(patientId) } : {}) })}`,
    ),
  staffSales: (from: string, to: string, branchCode?: string) =>
    api.get<StaffSalesReport>(
      `/api/reports/staff-sales?${new URLSearchParams({ from, to, ...(branchCode ? { branchCode } : {}) })}`,
    ),
}
