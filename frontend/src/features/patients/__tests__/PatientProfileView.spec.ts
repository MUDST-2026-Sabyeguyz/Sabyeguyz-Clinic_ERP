import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const viewSource = readFileSync(
  resolve(process.cwd(), 'src/features/patients/views/PatientProfileView.vue'),
  'utf-8',
)
const stylesheet = readFileSync(
  resolve(process.cwd(), 'src/features/patients/styles/patient-profile.css'),
  'utf-8',
)

describe('PatientProfileView edit screen', () => {
  it('renders the patient summary and Figma nationality controls while editing', () => {
    expect(viewSource).toContain('class="patient-edit-summary"')
    expect(viewSource).toContain('class="edit-nationality"')
    expect(viewSource).toContain('value="ต่างชาติ"')
  })

  it('uses the Figma name layout and edit card presentation', () => {
    expect(viewSource).toContain('class="patient-edit-name-grid"')
    expect(stylesheet).toContain('.patient-edit-summary')
    expect(stylesheet).toContain('.patient-edit-name-grid')
  })

})
