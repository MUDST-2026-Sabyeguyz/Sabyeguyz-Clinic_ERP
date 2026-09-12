import './styles/main.css'
import './styles/therapist-theme.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'

document.body.classList.toggle(
  'therapist-ui',
  (JSON.parse(localStorage.getItem('authUser') ?? 'null') as { role?: string } | null)?.role === 'THERAPIST',
)

const app = createApp(App)

app.use(createPinia())
app.use(router)

app.mount('#app')
