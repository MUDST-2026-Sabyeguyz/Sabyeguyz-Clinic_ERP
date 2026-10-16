import { createRouter, createWebHistory } from 'vue-router'
import { defineAsyncComponent, defineComponent, h, type Component } from 'vue'

type RoleViewLoader = () => Promise<{ default: Component }>
function roleView(manager: RoleViewLoader, therapist: RoleViewLoader) {
  const ManagerView = defineAsyncComponent(manager)
  const TherapistView = defineAsyncComponent(therapist)

  return defineComponent({
    name: 'RoleSpecificView',
    setup() {
      const user = JSON.parse(localStorage.getItem('authUser') ?? 'null') as {
        role?: string
      } | null
      const View = user?.role === 'MANAGER' ? ManagerView : TherapistView
      return () => h(View)
    },
  })
}

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
      path: '/staff/select-branch',
      name: 'manager-branch-selection',
      component: () => import('@/features/staff/views/ManagerBranchSelectionView.vue'),
      meta: { roles: ['manager'] },
    },
    {
      path: '/staff/patients/new',
      name: 'patient-registration',
      component: roleView(
        () => import('@/features/patients/views/ManagerPatientRegistrationView.vue'),
        () => import('@/features/patients/views/PatientRegistrationView.vue'),
      ),
      meta: { permission: 'Patient' },
    },
    {
      path: '/staff/patients',
      name: 'patient-search',
      component: roleView(
        () => import('@/features/patients/views/ManagerPatientSearchView.vue'),
        () => import('@/features/patients/views/PatientSearchView.vue'),
      ),
      meta: { permission: 'Patient' },
    },
    {
      path: '/staff/patients/:id/history',
      name: 'patient-history',
      component: roleView(
        () => import('@/features/patients/views/ManagerPatientHistoryView.vue'),
        () => import('@/features/patients/views/PatientHistoryView.vue'),
      ),
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
      component: roleView(
        () => import('@/features/patients/views/ManagerPatientProfileView.vue'),
        () => import('@/features/patients/views/PatientProfileView.vue'),
      ),
      meta: { permission: 'Patient' },
    },
    {
      path: '/staff/appointments',
      name: 'staff-appointments',
      component: roleView(
        () => import('@/features/appointments/views/ManagerAppointmentView.vue'),
        () => import('@/features/appointments/views/AppointmentView.vue'),
      ),
      meta: { permission: 'Visit' },
    },
    {
      path: '/staff/appointments/calendar',
      name: 'staff-appointment-calendar',
      component: roleView(
        () => import('@/features/appointments/views/ManagerAppointmentCalendarView.vue'),
        () => import('@/features/appointments/views/AppointmentCalendarView.vue'),
      ),
      meta: { permission: 'Visit' },
    },
    {
      path: '/staff/appointments/new',
      name: 'staff-appointment-create',
      component: roleView(
        () => import('@/features/appointments/views/ManagerAppointmentView.vue'),
        () => import('@/features/appointments/views/AppointmentView.vue'),
      ),
      meta: { permission: 'Visit' },
    },
    {
      path: '/staff/appointments/:id/edit',
      name: 'staff-appointment-edit',
      component: roleView(
        () => import('@/features/appointments/views/ManagerAppointmentView.vue'),
        () => import('@/features/appointments/views/AppointmentView.vue'),
      ),
      meta: { permission: 'Visit' },
    },
    {
      path: '/staff/services',
      name: 'staff-services',
      component: roleView(
        () => import('@/features/services/views/ManagerServicesView.vue'),
        () => import('@/features/services/views/servicesView.vue'),
      ),
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
      path: '/staff/commission/formula',
      name: 'commission-formula',
      component: () => import('@/features/commission/views/CommissionFormulaView.vue'),
      meta: { roles: ['manager', 'therapist'] },
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
      path: '/staff/commission/adjustments',
      name: 'commission-adjustments',
      component: () => import('@/features/commission/views/CommissionAdjustmentsView.vue'),
      meta: { roles: ['manager'] },
    },
    {
      path: '/staff/commission/adjustments/new',
      name: 'commission-adjustment-create',
      component: () => import('@/features/commission/views/CommissionAdjustmentCreateView.vue'),
      meta: { roles: ['manager'] },
    },
    {
      path: '/staff/commission/adjustments/:id/success',
      name: 'commission-adjustment-success',
      component: () => import('@/features/commission/views/CommissionAdjustmentSuccessView.vue'),
      meta: { roles: ['manager'] },
    },
    {
      path: '/staff/commission/adjustments/:id/schedule',
      name: 'commission-adjustment-schedule',
      component: () => import('@/features/commission/views/CommissionAdjustmentScheduleView.vue'),
      meta: { roles: ['manager'] },
    },
    {
      path: '/staff/commission/adjustments/:id/select-schedule',
      name: 'commission-adjustment-select-schedule',
      component: () => import('@/features/commission/views/CommissionAdjustmentSelectScheduleView.vue'),
      meta: { roles: ['manager'] },
    },
    {
      path: '/staff/commission/adjustments/:id/closed',
      name: 'commission-adjustment-closed',
      component: () => import('@/features/commission/views/CommissionAdjustmentClosedView.vue'),
      meta: { roles: ['manager'] },
    },
    {
      path: '/staff/commission/adjustments/:id/finalized',
      name: 'commission-adjustment-finalized',
      component: () => import('@/features/commission/views/CommissionAdjustmentFinalizedView.vue'),
      meta: { roles: ['manager'] },
    },
    {
      path: '/staff/commission/adjustments/:id/reopen',
      name: 'commission-adjustment-reopen',
      component: () => import('@/features/commission/views/CommissionAdjustmentReopenView.vue'),
      meta: { roles: ['manager'] },
    },
    {
      path: '/staff/commission/adjustments/:id/reopen-success',
      name: 'commission-adjustment-reopen-success',
      component: () => import('@/features/commission/views/CommissionAdjustmentReopenSuccessView.vue'),
      meta: { roles: ['manager'] },
    },
    {
      path: '/staff/commission/adjustments/:id/reopen-history',
      name: 'commission-adjustment-reopen-history',
      component: () => import('@/features/commission/views/CommissionAdjustmentReopenHistoryView.vue'),
      meta: { roles: ['manager'] },
    },
    {
      path: '/staff/commission/adjustments/:id/review',
      name: 'commission-adjustment-review',
      component: () => import('@/features/commission/views/CommissionAdjustmentReviewView.vue'),
      meta: { roles: ['manager'] },
    },
    {
      path: '/staff/commission/adjustments/:id/payment-success',
      name: 'commission-adjustment-payment-success',
      component: () => import('@/features/commission/views/CommissionAdjustmentPaymentSuccessView.vue'),
      meta: { roles: ['manager'] },
    },
    {
      path: '/staff/commission/adjustments/:id',
      name: 'commission-adjustment-detail',
      component: () => import('@/features/commission/views/CommissionAdjustmentDetailView.vue'),
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
      path: '/staff/reports/transaction/tier-round',
      name: 'transaction-tier-round',
      component: () => import('@/features/reports/views/TransactionTierRoundDetailView.vue'),
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
