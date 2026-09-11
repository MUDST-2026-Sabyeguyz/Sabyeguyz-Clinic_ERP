import './styles/main.css'
import './styles/therapist-theme.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'

document.body.classList.toggle(
  'therapist-ui',
  localStorage.getItem('mockStaffRole') === 'therapist',
)

const app = createApp(App)

app.use(createPinia())
app.use(router)

app.mount('#app')
