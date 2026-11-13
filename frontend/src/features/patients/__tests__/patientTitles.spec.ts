import { describe, expect, it } from 'vitest'
import { patientTitleOptions, titleForNationality, titleForGender, genderForTitle } from '../data/patientTitles'

describe('database patient titles', () => {
  it('uses the existing English enum values for foreign patients', () => {
    expect(patientTitleOptions(true).map((item) => item.value)).toEqual(['Mr.', 'Miss', 'Mrs.', 'Boy', 'Girl'])
    expect(patientTitleOptions(false).map((item) => item.value)).toEqual(['นาย', 'นางสาว', 'นาง', 'เด็กชาย', 'เด็กหญิง'])
  })
  it('preserves the equivalent title when nationality changes in either direction', () => {
    expect(titleForNationality('นาง', true)).toBe('Mrs.')
    expect(titleForNationality('Boy', false)).toBe('เด็กชาย')
    expect(titleForNationality('Miss', true)).toBe('Miss')
  })
  it('keeps gender synchronization within the selected language', () => {
    expect(genderForTitle('Boy')).toBe('ชาย')
    expect(genderForTitle('Mrs.')).toBe('หญิง')
    expect(titleForGender('Boy', 'ชาย', true)).toBe('Boy')
    expect(titleForGender('Mr.', 'หญิง', true)).toBe('Miss')
    expect(titleForGender('นาง', 'ชาย', false)).toBe('นาย')
  })
})
