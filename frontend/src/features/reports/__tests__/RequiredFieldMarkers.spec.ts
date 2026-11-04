import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

describe('backend-aligned required field markers', () => {
  it('marks the required source month on a commission adjustment', () => {
    const source = readFileSync(
      resolve(process.cwd(), 'src/features/commission/views/CommissionAdjustmentCreateView.vue'),
      'utf-8',
    )

    expect(source).toContain('งวดต้นทาง *')
  })
})
