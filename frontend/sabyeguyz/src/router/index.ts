import { createRouter, createWebHistory } from 'vue-router'
import PatientRegistrationView from '../views/PatientRegistrationView.vue'
import PatientSearchView from '../views/PatientSearchView.vue'
import PatientProfileView from '../views/PatientProfileView.vue'
import PlaceholderView from '../views/PlaceholderView.vue'
import AppointmentView from '../views/AppointmentView.vue'
import TreatmentView from '../views/TreatmentView.vue'
import BranchManagementView from '../views/BranchManagementView.vue'
import StaffManagementView from '../views/StaffManagementView.vue'
import CourseManagementView from '../views/CourseManagementView.vue'
import LoginView from '../views/LoginView.vue'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
    {
      path: '/',
      name: 'dashboard',
      component: PlaceholderView,
      props: { label: 'Dashboard' },
    },
    {
      path: '/patients',
      name: 'patient-search',
      component: PatientSearchView,
    },
    {
      path: '/patients/register',
      name: 'patient-registration',
      component: PatientRegistrationView,
    },
    {
      path: '/patients/:id(\\d+)',
      name: 'patient-profile',
      component: PatientProfileView,
    },
    { path: '/branches', name: 'branches', component: BranchManagementView },
    { path: '/staff', name: 'staff-management', component: StaffManagementView },
    { path: '/courses', name: 'course-management', component: CourseManagementView },
    ...['appointments', 'treatment', 'courses', 'payments', 'staff', 'reports', 'settings'].map(
      (section) => ({
        path: `/${section}`,
        name: section,
        component: section === 'appointments' ? AppointmentView : section === 'treatment' ? TreatmentView : PlaceholderView,
        props: { label: section.charAt(0).toUpperCase() + section.slice(1) },
      }),
    ),
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.public || auth.isAuthenticated) return true
  return { name: 'login', query: { redirect: to.fullPath } }
})

export default router
