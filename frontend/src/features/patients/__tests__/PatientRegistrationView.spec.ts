import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const viewSource = readFileSync(
  resolve(process.cwd(), 'src/features/patients/views/PatientRegistrationView.vue'),
  'utf-8',
)
const stylesheet = readFileSync(
  resolve(process.cwd(), 'src/features/patients/styles/patient-registration.css'),
  'utf-8',
)

describe('PatientRegistrationView', () => {
  it('renders the Figma personal-data layout for Thai and foreign patients', () => {
    expect(viewSource).toContain('class="personal-details-grid"')
    expect(viewSource).toContain('class="patient-name-grid"')
    expect(viewSource).toContain('v-model.trim="form.middleName"')
    expect(viewSource).toContain("isForeign ? 'เลขพาสปอร์ต' : 'เลขบัตรประชาชน'")
  })

  it('uses the registration card layout from the patient Figma screens', () => {
    expect(stylesheet).toContain('.registration-section + .registration-section')
    expect(stylesheet).toContain('.patient-name-grid')
    expect(stylesheet).toContain('.personal-details-grid')
  })
})
