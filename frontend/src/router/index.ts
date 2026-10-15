import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'staff-login',
      component: () => import('@/features/auth/views/StaffLoginView.vue'),
    },
    {
      path: '/staff/login',
      redirect: '/',
    },
    {
      path: '/staff/patients/new',
      name: 'patient-registration',
      component: () => import('@/features/patients/views/PatientRegistrationView.vue'),
      meta: { permission: 'Patient' },
    },
    {
      path: '/staff/patients',
      name: 'patient-search',
      component: () => import('@/features/patients/views/PatientSearchView.vue'),
      meta: { permission: 'Patient' },
    },
    {
      path: '/staff/patients/:id/history',
      name: 'patient-history',
      component: () => import('@/features/patients/views/PatientHistoryView.vue'),
      meta: { permission: 'Patient' },
    },
    {
      path: '/staff/patients/:patientId/courses/:courseId',
      name: 'patient-course-history',
      component: () => import('@/features/courses/views/CourseActionView.vue'),
      meta: { permission: 'Patient' },
    },
    {
      path: '/staff/patients/:id',
      name: 'patient-profile',
      component: () => import('@/features/patients/views/PatientProfileView.vue'),
      meta: { permission: 'Patient' },
    },
    {
      path: '/staff/appointments',
      name: 'staff-appointments',
      component: () => import('@/features/appointments/views/AppointmentView.vue'),
      meta: { permission: 'Visit' },
    },
    {
      path: '/staff/appointments/calendar',
      name: 'staff-appointment-calendar',
      component: () => import('@/features/appointments/views/AppointmentCalendarView.vue'),
      meta: { permission: 'Visit' },
    },
    {
      path: '/staff/appointments/new',
      name: 'staff-appointment-create',
      component: () => import('@/features/appointments/views/AppointmentView.vue'),
      meta: { permission: 'Visit' },
    },
    {
      path: '/staff/appointments/:id/edit',
      name: 'staff-appointment-edit',
      component: () => import('@/features/appointments/views/AppointmentView.vue'),
      meta: { permission: 'Visit' },
    },
    {
      path: '/staff/services',
      name: 'staff-services',
      component: () => import('@/features/services/views/servicesView.vue'),
      meta: { permission: 'Treatment' },
    },
    {
      path: '/staff/receipts/:id',
      name: 'staff-receipt',
      component: () => import('@/features/receipts/views/ReceiptView.vue'),
      meta: { permission: 'Treatment' },
    },
    {
      path: '/staff/billing',
      redirect: '/staff/services',
    },
    {
      path: '/staff/courses',
      name: 'staff-courses',
      component: () => import('@/features/courses/views/CourseActionView.vue'),
      meta: { permission: 'Course' },
    },
    {
      path: '/staff/transactions',
      name: 'staff-transactions',
      component: () => import('@/features/transactions/views/TransactionView.vue'),
      meta: { permission: 'Transaction' },
    },
    {
      path: '/staff/commission',
      name: 'staff-commission',
      component: () => import('@/features/commission/views/CommissionOverviewView.vue'),
      meta: { permission: 'Commission' },
    },
    {
      path: '/staff/commission/transactions',
      name: 'commission-transactions',
      component: () => import('@/features/commission/views/CommissionView.vue'),
      meta: { permission: 'Commission' },
    },
    {
      path: '/staff/compensation',
      name: 'therapist-compensation',
      component: () => import('@/features/commission/views/TherapistCompensationView.vue'),
      meta: { roles: ['therapist'] },
    },
    {
      path: '/staff/compensation/commission/:id?',
      name: 'therapist-commission-detail',
      component: () => import('@/features/commission/views/TherapistCommissionDetailView.vue'),
      meta: { roles: ['therapist'] },
    },
    {
      path: '/staff/compensation/commission/:id?/source',
      name: 'therapist-commission-source',
      component: () => import('@/features/commission/views/TherapistCommissionSourceView.vue'),
      meta: { roles: ['therapist'] },
    },
    {
      path: '/staff/compensation/treatment/:id?',
      name: 'therapist-treatment-fee-detail',
      component: () => import('@/features/commission/views/TherapistTreatmentFeeDetailView.vue'),
      meta: { roles: ['therapist'] },
    },
    {
      path: '/staff/compensation/treatment/:id?/source',
      name: 'therapist-treatment-source',
      component: () => import('@/features/commission/views/TherapistTreatmentSourceView.vue'),
      meta: { roles: ['therapist'] },
    },
    {
      path: '/staff/compensation/payments',
      name: 'therapist-payment-history',
      component: () => import('@/features/commission/views/TherapistPaymentHistoryView.vue'),
      meta: { roles: ['therapist'] },
    },
    {
      path: '/staff/compensation/adjustment/:id?',
      name: 'therapist-adjustment-detail',
      component: () => import('@/features/commission/views/TherapistAdjustmentDetailView.vue'),
      meta: { roles: ['therapist'] },
    },
    {
      path: '/staff/commission/tiers',
      name: 'commission-tiers',
      component: () => import('@/features/commission/views/CommissionTierView.vue'),
      meta: { roles: ['manager'] },
    },
    {
      path: '/staff/commission/closing',
      name: 'commission-closing',
      component: () => import('@/features/commission/views/CommissionClosingView.vue'),
      meta: { roles: ['manager'] },
    },
    {
      path: '/staff/commission/payments',
      name: 'commission-payments',
      component: () => import('@/features/commission/views/CommissionPaymentsView.vue'),
      meta: { roles: ['manager'] },
    },
    {
      path: '/staff/commission/adjustments',
      name: 'commission-adjustments',
      component: () => import('@/features/commission/views/CommissionAdjustmentsView.vue'),
      meta: { roles: ['manager'] },
    },
    {
      path: '/staff/refunds',
      name: 'staff-refunds',
      component: () => import('@/features/refunds/views/RefundView.vue'),
      meta: { roles: ['manager'] },
    },
    {
      path: '/staff/commission/staff/:staffId',
      name: 'commission-person-detail',
      component: () => import('@/features/commission/views/CommissionPersonDetailView.vue'),
      meta: { roles: ['manager'] },
    },
    {
      path: '/staff/commission/compensation-settings',
      name: 'commission-compensation-settings',
      component: () => import('@/features/commission/views/CommissionCompensationSettingsView.vue'),
      meta: { roles: ['manager'] },
    },
    {
      path: '/staff/commission/courses/:courseId',
      name: 'commission-course-detail',
      component: () => import('@/features/commission/views/CourseCommissionDetailView.vue'),
      meta: { roles: ['manager'] },
    },
    {
      path: '/staff/dashboard',
      name: 'staff-dashboard',
      component: () => import('@/features/dashboard/views/DashboardView.vue'),
      meta: { roles: ['manager'] },
    },
    {
      path: '/staff/reports',
      name: 'staff-reports',
      component: () => import('@/features/reports/views/ReportsView.vue'),
      meta: { roles: ['manager'] },
    },
    {
      path: '/staff/personnel',
      name: 'staff-personnel',
      component: () => import('@/features/commission/views/PersonnelTransactionsView.vue'),
      meta: { roles: ['manager'] },
    },
    {
      path: '/staff/calendar',
      name: 'staff-calendar',
      component: () => import('@/features/settings/views/CalendarSettingsView.vue'),
      meta: { roles: ['manager'] },
    },
    {
      path: '/staff/settings',
      name: 'staff-settings',
      component: () => import('@/features/settings/views/BackOfficeView.vue'),
      meta: { permission: 'Setup' },
    },
  ],
})

router.beforeEach((to) => {
  if (!to.path.startsWith('/staff/')) return true
  const authUser = JSON.parse(localStorage.getItem('authUser') ?? 'null') as {
    role?: string
  } | null
  const role =
    authUser?.role === 'MANAGER' ? 'manager' : authUser?.role === 'THERAPIST' ? 'therapist' : null
  if (role !== 'manager' && role !== 'therapist') {
    localStorage.removeItem('authToken')
    localStorage.removeItem('authUser')
    return { name: 'staff-login' }
  }
  const allowedRoles = to.meta.roles as string[] | undefined
  if (allowedRoles && !allowedRoles.includes(role)) return { name: 'patient-search' }
  const requiredPermission = to.meta.permission as string | undefined
  const permissions =
    role === 'manager'
      ? 'Patient, Visit, Treatment, Course, Transaction, Commission, Setup'
      : 'Patient, Visit, Treatment, Course, Transaction'
  if (role !== 'manager' && requiredPermission && !permissions.includes(requiredPermission)) {
    const destinations: Array<[string, string]> = [
      ['Patient', '/staff/patients'],
      ['Visit', '/staff/appointments'],
      ['Treatment', '/staff/services'],
      ['Course', '/staff/courses'],
      ['Transaction', '/staff/transactions'],
      ['Commission', '/staff/commission'],
      ['Setup', '/staff/settings'],
    ]
    return destinations.find(([permission]) => permissions.includes(permission))?.[1] ?? '/'
  }
  return true
})

router.afterEach(() => {
  const authUser = JSON.parse(localStorage.getItem('authUser') ?? 'null') as {
    role?: string
  } | null
  const usesSharedStaffUi = authUser?.role === 'THERAPIST' || authUser?.role === 'MANAGER'
  document.body.classList.toggle('therapist-ui', usesSharedStaffUi)
})

export default router
