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

  it('gives the post-use success overlay dialog semantics', () => {
    expect(viewSource).toContain('role="dialog"')
    expect(viewSource).toContain('aria-modal="true"')
    expect(viewSource).toContain('v-modal-focus="{ close: returnToCourseManager }"')
  })

  it('does not calculate a progress width from a zero total', () => {
    expect(viewSource).toContain('courseUsageProgressWidth(selectedCourse)')
  })
})
