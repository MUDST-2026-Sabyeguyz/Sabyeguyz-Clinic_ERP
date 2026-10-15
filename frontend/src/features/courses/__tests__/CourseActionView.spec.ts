import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

describe('CourseActionView', () => {
  it('does not show the redundant expiry edit button on patient course management cards', () => {
    const source = readFileSync(
      resolve(process.cwd(), 'src/features/courses/views/CourseActionView.vue'),
      'utf8',
    )

    expect(source).not.toMatch(
      /<button[^>]*title="แก้ไขผ่านเมนูต่ออายุคอร์ส"[^>]*>\s*แก้ไขวันหมดอายุ\s*<\/button>/,
    )
  })
})
