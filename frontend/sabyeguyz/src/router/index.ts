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
      path: '/staff/patients/:hn',
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
      path: '/staff/services',
      name: 'staff-services',
      component: () => import('@/features/services/views/servicesView.vue'),
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
      component: () => import('@/features/commission/views/CommissionView.vue'),
      meta: { permission: 'Commission' },
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
      path: '/staff/settings',
      name: 'staff-settings',
      component: () => import('@/features/settings/views/BackOfficeView.vue'),
      meta: { permission: 'Setup' },
    },
  ],
})

router.beforeEach((to) => {
  if (!to.path.startsWith('/staff/')) return true
  const role = localStorage.getItem('mockStaffRole')
  const token = localStorage.getItem('authToken')
  if (!token || (role !== 'manager' && role !== 'therapist')) {
    localStorage.removeItem('mockStaffRole')
    localStorage.removeItem('mockStaffEmail')
    return { name: 'staff-login' }
  }
  const allowedRoles = to.meta.roles as string[] | undefined
  if (allowedRoles && !allowedRoles.includes(role)) return { name: 'patient-search' }
  const requiredPermission = to.meta.permission as string | undefined
  const permissions =
    localStorage.getItem('mockStaffPermissions') ?? 'Patient, Visit, Treatment, Course, Transaction'
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
  document.body.classList.toggle(
    'therapist-ui',
    localStorage.getItem('mockStaffRole') === 'therapist',
  )
})

export default router
