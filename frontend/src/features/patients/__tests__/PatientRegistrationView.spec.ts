import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const viewSource = readFileSync(
  resolve(process.cwd(), 'src/features/patients/views/PatientRegistrationView.vue'),
  'utf-8',
)
const managerViewSource = readFileSync(
  resolve(process.cwd(), 'src/features/patients/views/ManagerPatientRegistrationView.vue'),
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

  it('submits separate Thai name parts for the main PatientController contract', () => {
    expect(viewSource).toContain('firstName: form.firstName')
    expect(viewSource).toContain('middleName: form.middleName || null')
    expect(viewSource).toContain('lastName: form.lastName')
    expect(viewSource).not.toContain('username: `patient-${Date.now()}`')
    expect(viewSource).not.toContain('password: `Temp${form.identityNumber.slice(-6)}!`')
  })

  it.each([viewSource, managerViewSource])(
    'marks and validates only fields required by the registration backend',
    (source) => {
      expect(source).toContain('คำนำหน้า <em>*</em>')
      expect(source).toContain("{{ isForeign ? 'เลขพาสปอร์ต' : 'เลขบัตรประชาชน' }} <em v-if=\"!isForeign\">*</em>")
      expect(source).toContain('สาขาที่ลงทะเบียน <em>*</em>')
      expect(source).toContain('กลุ่มลูกค้า <em v-if="!isForeign">*</em>')
      expect(source).toContain('if (!form.identityNumber.trim()) return isForeign.value')
      expect(source).toContain('!form.birthDate || form.birthDate < clinicToday()')
      expect(source).not.toContain('วันเกิด <em v-if="!isForeign">*</em>')
    },
  )
})
