import './styles/main.css'
import './styles/therapist-theme.css'
import './features/commission/styles/commission-manager.css'
import './styles/app-theme.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router, { prefetchRoute } from './router'

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

// Warm the next page's chunk on hover / keyboard focus / touch-start, before the click completes.
const warm = (event: Event) => {
  const link = (event.target as Element | null)?.closest?.('a[href^="/"]')
  const href = link?.getAttribute('href')
  if (href) prefetchRoute(href)
}
for (const type of ['pointerover', 'pointerdown', 'focusin']) {
  document.addEventListener(type, warm, { passive: true, capture: true })
}
