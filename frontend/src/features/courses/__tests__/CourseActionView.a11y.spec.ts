import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const viewSource = readFileSync(
  resolve(process.cwd(), 'src/features/courses/views/CourseActionView.vue'),
  'utf-8',
)

describe('CourseActionView course manager accessibility', () => {
  it('exposes the course-status filter as keyboard-operable tabs', () => {
    expect(viewSource).toContain('role="tablist"')
    expect(viewSource).toContain('role="tab"')
    expect(viewSource).toContain(':aria-selected="courseStatusTab === \'active\'"')
    expect(viewSource).toContain('@keydown="handleCourseStatusTabKeydown"')
  })

  it('labels the post-use success summary as a page region', () => {
    expect(viewSource).toContain('role="region"')
    expect(viewSource).toContain('aria-labelledby="after-use-success-title"')
    expect(viewSource).toContain('<h1 id="after-use-success-title">')
  })

  it('gives the confirmation dialogs modal semantics and focus handling', () => {
    expect(viewSource).toContain('role="dialog"')
    expect(viewSource).toContain('aria-modal="true"')
    expect(viewSource).toContain('v-modal-focus="{ close: () => (usageConfirmDialog = false)')
  })

  it('does not calculate a progress width from a zero total', () => {
    expect(viewSource).toContain('courseUsageProgressWidth(selectedCourse)')
  })
})
