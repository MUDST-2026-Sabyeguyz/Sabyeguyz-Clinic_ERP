import type { ObjectDirective } from 'vue'

// Associate existing inline errors with their field without duplicating validation rules.
function syncErrors(form: HTMLFormElement) {
  form.querySelectorAll('label').forEach((label, index) => {
    const error = label.querySelector<HTMLElement>('.required-error')
    const controls = label.querySelectorAll<HTMLElement>('input, select, textarea, button')
    if (error) error.id ||= 'registration-error-' + index
    controls.forEach((control) => {
      if (error) {
        control.setAttribute('aria-invalid', 'true')
        control.setAttribute('aria-describedby', error.id)
      } else {
        control.removeAttribute('aria-invalid')
        if (control.getAttribute('aria-describedby')?.startsWith('registration-error-'))
          control.removeAttribute('aria-describedby')
      }
    })
  })
}
export const vRegistrationErrors: ObjectDirective<HTMLFormElement> = {
  mounted: syncErrors,
  updated: syncErrors,
}
