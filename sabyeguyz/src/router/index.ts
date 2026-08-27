import { createRouter, createWebHistory } from 'vue-router'
import PatientRegistrationView from '../views/PatientRegistrationView.vue'
import PlaceholderView from '../views/PlaceholderView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'dashboard',
      component: PlaceholderView,
      props: { label: 'Dashboard' },
    },
    {
      path: '/patients/register',
      name: 'patient-registration',
      component: PatientRegistrationView,
    },
    ...['appointments', 'treatment', 'courses', 'payments', 'staff', 'reports', 'settings'].map((section) => ({
      path: `/${section}`,
      name: section,
      component: PlaceholderView,
      props: { label: section.charAt(0).toUpperCase() + section.slice(1) },
    })),
  ],
})

export default router
