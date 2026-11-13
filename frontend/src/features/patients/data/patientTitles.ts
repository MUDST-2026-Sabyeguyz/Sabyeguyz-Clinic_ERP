// Matches title_enum and chk_patient_title_customer_type in the database.
const titles = [
  ['นาย', 'Mr.', 'ชาย'],
  ['นางสาว', 'Miss', 'หญิง'],
  ['นาง', 'Mrs.', 'หญิง'],
  ['เด็กชาย', 'Boy', 'ชาย'],
  ['เด็กหญิง', 'Girl', 'หญิง'],
] as const

export function patientTitleOptions(foreign: boolean) {
  return titles.map((row) => ({ value: row[foreign ? 1 : 0], label: row[foreign ? 1 : 0] }))
}

export function titleForNationality(title: string, foreign: boolean) {
  const row = titles.find((item) => item[0] === title || item[1] === title) ?? titles[0]
  return row[foreign ? 1 : 0]
}

export function genderForTitle(title: string) {
  return titles.find((item) => item[0] === title || item[1] === title)?.[2]
}

export function titleForGender(title: string, gender: string, foreign: boolean) {
  if (genderForTitle(title) === gender) return titleForNationality(title, foreign)
  return titleForNationality(gender === 'หญิง' ? 'นางสาว' : 'นาย', foreign)
}
