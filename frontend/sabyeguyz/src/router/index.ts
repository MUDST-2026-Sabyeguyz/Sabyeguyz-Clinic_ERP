import { createRouter, createWebHistory } from 'vue-router'
import PatientRegistrationView from '../views/PatientRegistrationView.vue'
import PatientSearchView from '../views/PatientSearchView.vue'
import PatientProfileView from '../views/PatientProfileView.vue'
import AppointmentView from '../views/AppointmentManagerView.vue'
import TreatmentView from '../views/TreatmentView.vue'
import BranchManagementView from '../views/BranchManagementView.vue'
import StaffManagementView from '../views/StaffManagementView.vue'
import CourseManagementView from '../views/CourseManagementView.vue'
import PatientCoursesView from '../views/PatientCoursesView.vue'
import LoginView from '../views/LoginView.vue'
import PaymentsView from '../views/PaymentsView.vue'
import DashboardView from '../views/DashboardView.vue'
import ReportsView from '../views/ReportsView.vue'
import SettingsView from '../views/SettingsView.vue'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
    { path: '/', name: 'dashboard', component: DashboardView },
    { path: '/patients', name: 'patient-search', component: PatientSearchView },
    { path: '/patients/register', name: 'patient-registration', component: PatientRegistrationView },
    { path: '/patients/:id(\\d+)', name: 'patient-profile', component: PatientProfileView },
    { path: '/branches', name: 'branches', component: BranchManagementView },
    { path: '/staff', name: 'staff-management', component: StaffManagementView },
    { path: '/courses', name: 'course-management', component: CourseManagementView },
    { path: '/course-ledger', name: 'course-ledger', component: PatientCoursesView },
    { path: '/appointments', name: 'appointments', component: AppointmentView },
    { path: '/treatment', name: 'treatment', component: TreatmentView },
    { path: '/payments', name: 'payments', component: PaymentsView },
    { path: '/reports', name: 'reports', component: ReportsView },
    { path: '/settings', name: 'settings', component: SettingsView },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.public || auth.isAuthenticated) return true
  return { name: 'login', query: { redirect: to.fullPath } }
})

export default router
