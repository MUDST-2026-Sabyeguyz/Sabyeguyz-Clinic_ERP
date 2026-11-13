import './styles/main.css'
import './styles/therapist-theme.css'
import './features/commission/styles/commission-manager.css'
import './styles/app-theme.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'

document.body.classList.toggle(
  'therapist-ui',
  ['THERAPIST', 'MANAGER'].includes(
    (JSON.parse(localStorage.getItem('authUser') ?? 'null') as { role?: string } | null)?.role ??
      '',
  ),
)

const app = createApp(App)

app.use(createPinia())
app.use(router)

app.mount('#app')
