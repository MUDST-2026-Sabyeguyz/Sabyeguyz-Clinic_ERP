import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

// หน้าคอร์สแยกเป็นหน้าหลักกับพาเนลย่อย 4 ส่วน จึงตรวจจากซอร์สรวมของทุกไฟล์
const viewSource = [
  'views/CourseActionView.vue',
  'components/CourseUsePanel.vue',
  'components/CourseTransferPanel.vue',
  'components/CourseHistoryPanel.vue',
  'components/CourseManagePanel.vue',
]
  .map((file) => readFileSync(resolve(process.cwd(), 'src/features/courses', file), 'utf-8'))
  .join('\n')

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

})
