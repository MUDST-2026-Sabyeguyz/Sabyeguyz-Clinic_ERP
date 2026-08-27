import { createRouter, createWebHistory } from 'vue-router'
import PatientRegistrationView from '../views/PatientRegistrationView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: '/patients/register',
    },
    {
      path: '/patients/register',
      name: 'patient-registration',
      component: PatientRegistrationView,
    },
  ],
})

export default router
