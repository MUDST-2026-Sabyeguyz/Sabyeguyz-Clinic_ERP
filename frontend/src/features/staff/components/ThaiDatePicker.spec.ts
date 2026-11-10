import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import ThaiDatePicker from './ThaiDatePicker.vue'

describe('date picker outside clipped containers', () => {
  it('anchors the calendar to the field and keeps date selection working', async () => {
    const view = mount(ThaiDatePicker, {
      attachTo: document.body,
      props: { modelValue: '2026-11-01', escapeClipping: true },
    })
    vi.spyOn(view.get('.date-trigger').element, 'getBoundingClientRect').mockReturnValue({
      left: 120, right: 420, top: 100, bottom: 144, width: 300, height: 44,
      x: 120, y: 100, toJSON: () => ({}),
    })
    await view.get('.date-trigger').trigger('click')
    await flushPromises()
    expect(view.get('.calendar').attributes('popover')).toBe('manual')
    expect((view.get('.calendar').element as HTMLElement).style.position).toBe('fixed')
    expect((view.get('.calendar').element as HTMLElement).style.top).toBe('150px')
    await view.get('.days button').trigger('click')
    expect(view.emitted('update:modelValue')?.[0]).toEqual(['2026-11-01'])
    expect(view.find('.calendar').exists()).toBe(false)
    view.unmount()
  })
  it('does not change positioning for existing unported date pickers', async () => {
    const view = mount(ThaiDatePicker, { props: { modelValue: '' } })
    await view.get('.date-trigger').trigger('click')
    expect(view.get('.calendar').attributes('popover')).toBeUndefined()
    expect(view.get('.calendar').attributes('style')).toBeUndefined()
    view.unmount()
  })
})
