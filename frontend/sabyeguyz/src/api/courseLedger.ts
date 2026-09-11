import { workflowRequest as request } from './workflowHttp'

export interface CoursePurchase {
  id: number
  patient: { id: number; hn: string; fullName: string }
  branchCode: string
  course: { id: number; code: string; name: string }
  price: number
  sessions: number
  bonusSessions: number
  purchased: number
  used: number
  remaining: number
  purchasedAt: string
  expiresAt: string
  expired: boolean
  usable: boolean
  status: string
}
export interface CourseMovement {
  id: number; type: string; quantity: number; balanceAfter: number; reason: string | null
  appointmentId: number | null; reversesMovementId: number | null; createdAt: string
}
export interface CourseLedger { purchase: CoursePurchase; movements: CourseMovement[] }
export const purchaseCourse = (body: { patientId: number; courseId: number; branchCode: string }) => request<CoursePurchase>('/api/course-purchases', 'POST', body)
export const patientCourses = (id: number) => request<CoursePurchase[]>(`/api/patients/${id}/courses`)
export const courseLedger = (id: number) => request<CourseLedger>(`/api/course-purchases/${id}`)
export const useCourse = (body: { coursePurchaseId: number; appointmentId?: number; note?: string }) => request<CoursePurchase>('/api/course-usages', 'POST', body)
export const reverseUsage = (id: number, reason: string) => request<CoursePurchase>(`/api/course-movements/${id}/reverse`, 'POST', { reason })
export const cancelPurchase = (id: number, reason: string) => request<CoursePurchase>(`/api/course-purchases/${id}/cancel`, 'POST', { reason })
