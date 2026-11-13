import type { ObjectDirective } from 'vue'

type ModalOptions = { close: () => void; busy?: boolean } | null
type ModalState = { update: (value: ModalOptions) => void; dispose: () => void }
const states = new WeakMap<HTMLElement, ModalState>()
const stack: HTMLElement[] = []
let previousOverflow = ''
const focusable =
  'button:not(:disabled), a[href], input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])'

export const vModalFocus: ObjectDirective<HTMLElement, ModalOptions> = {
  mounted(element, binding) {
    let options = binding.value
    let previous: HTMLElement | null = null
    let active = false
    const targets = () =>
      Array.from(element.querySelectorAll<HTMLElement>(focusable)).filter(
        (item) => item.getClientRects().length > 0 && !item.closest('[inert]'),
      )
    const focusFirst = () => (targets()[0] ?? element).focus()
    const isTop = () => stack[stack.length - 1] === element
    const onKey = (event: KeyboardEvent) => {
      if (!active || !isTop()) return
      if (event.key === 'Escape') {
        event.preventDefault()
        event.stopPropagation()
        if (!options?.busy) options?.close()
      }
      if (event.key !== 'Tab') return
      const items = targets()
      const first = items[0]
      const last = items[items.length - 1]
      if (!first || !last) {
        event.preventDefault()
        element.focus()
        return
      }
      if (
        event.shiftKey &&
        (document.activeElement === first || document.activeElement === element)
      ) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first.focus()
      }
    }
    const onFocus = (event: FocusEvent) => {
      if (active && isTop() && event.target instanceof Node && !element.contains(event.target))
        focusFirst()
    }
    const deactivate = () => {
      if (!active) return
      active = false
      stack.splice(stack.indexOf(element), 1)
      document.removeEventListener('keydown', onKey, true)
      document.removeEventListener('focusin', onFocus)
      if (stack.length === 0) document.body.style.overflow = previousOverflow
      queueMicrotask(() => {
        if (previous?.isConnected) previous.focus()
        else if (stack.length === 0) document.querySelector<HTMLElement>('.appointment-hero-actions button')?.focus()
      })
    }
    const update = (value: ModalOptions) => {
      options = value
      if (!options) {
        deactivate()
        return
      }
      if (active) return
      active = true
      previous = document.activeElement instanceof HTMLElement ? document.activeElement : null
      element.tabIndex = -1
      if (stack.length === 0) {
        previousOverflow = document.body.style.overflow
        document.body.style.overflow = 'hidden'
      }
      stack.push(element)
      document.addEventListener('keydown', onKey, true)
      document.addEventListener('focusin', onFocus)
      queueMicrotask(() => {
        if (active && isTop()) focusFirst()
      })
    }
    states.set(element, { update, dispose: deactivate })
    update(options)
  },
  updated(element, binding) {
    states.get(element)?.update(binding.value)
  },
  beforeUnmount(element) {
    states.get(element)?.dispose()
    states.delete(element)
  },
}
