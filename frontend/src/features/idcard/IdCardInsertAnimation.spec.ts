import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import IdCardInsertAnimation from './IdCardInsertAnimation.vue'

describe('IdCardInsertAnimation', () => {
  it('draws a card going into a reader, described for screen readers', () => {
    const wrapper = mount(IdCardInsertAnimation)
    const svg = wrapper.get('svg.id-card-graphic')
    expect(svg.attributes('role')).toBe('img')
    expect(svg.attributes('aria-label')).toContain('เสียบบัตรประชาชน')
    expect(wrapper.find('.card-moving').exists()).toBe(true)
    expect(wrapper.find('.reader-body').exists()).toBe(true)
  })
})
