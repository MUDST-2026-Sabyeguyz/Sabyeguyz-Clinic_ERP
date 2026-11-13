import { describe, expect, it } from 'vitest'
import type { IdCardData } from '@/api/client'
import { cardExpired, mapCardToRegistration } from './cardToForm'

const card = (over: Partial<IdCardData> = {}): IdCardData => ({
  nationalId: '1101700005119',
  titleTh: 'นาย',
  firstNameTh: 'สมชาย',
  middleNameTh: null,
  lastNameTh: 'ใจดี',
  fullNameEn: 'Somchai Jaidee',
  birthDate: '1997-01-31',
  gender: 'M',
  address: '88/8 หมู่ที่ 3 ตำบลจรเข้บัว',
  issueDate: '2020-02-02',
  expiryDate: null,
  lifetime: true,
  ...over,
})

describe('mapCardToRegistration', () => {
  it('fills the identity, names, birth date, gender and address from the card', () => {
    expect(mapCardToRegistration(card())).toEqual({
      identityDocumentType: 'NATIONAL_ID',
      identityNumber: '1101700005119',
      nationality: 'ไทย',
      title: 'นาย',
      firstName: 'สมชาย',
      middleName: '',
      lastName: 'ใจดี',
      fullNameEnglish: 'Somchai Jaidee',
      birthDate: '1997-01-31',
      gender: 'ชาย',
      address: '88/8 หมู่ที่ 3 ตำบลจรเข้บัว',
    })
  })

  it('maps the common Thai title spellings to the five titles the system supports', () => {
    expect(mapCardToRegistration(card({ titleTh: 'น.ส.' })).title).toBe('นางสาว')
    expect(mapCardToRegistration(card({ titleTh: 'นางสาว' })).title).toBe('นางสาว')
    expect(mapCardToRegistration(card({ titleTh: 'นาง' })).title).toBe('นาง')
    expect(mapCardToRegistration(card({ titleTh: 'ด.ช.' })).title).toBe('เด็กชาย')
    expect(mapCardToRegistration(card({ titleTh: 'เด็กหญิง' })).title).toBe('เด็กหญิง')
  })

  it('leaves the title for the staff to choose when the card has another title', () => {
    expect('title' in mapCardToRegistration(card({ titleTh: 'ดร.' }))).toBe(false)
    expect('title' in mapCardToRegistration(card({ titleTh: null }))).toBe(false)
  })

  it('takes the gender from the card, not from the title', () => {
    expect(mapCardToRegistration(card({ titleTh: 'นาย', gender: 'F' })).gender).toBe('หญิง')
    expect('gender' in mapCardToRegistration(card({ gender: null }))).toBe(false)
  })

  it('keeps a middle name and skips fields the card did not give', () => {
    const mapped = mapCardToRegistration(
      card({ middleNameTh: 'กลาง', fullNameEn: null, address: null, birthDate: null }),
    )
    expect(mapped.middleName).toBe('กลาง')
    expect('fullNameEnglish' in mapped).toBe(false)
    expect('address' in mapped).toBe(false)
    expect('birthDate' in mapped).toBe(false)
  })
})

describe('cardExpired', () => {
  it('is never expired for a lifetime card', () => {
    expect(cardExpired(card({ lifetime: true, expiryDate: null }), '2030-01-01')).toBe(false)
  })

  it('is expired once the expiry date is before today', () => {
    expect(cardExpired(card({ lifetime: false, expiryDate: '2025-12-31' }), '2026-11-10')).toBe(true)
    expect(cardExpired(card({ lifetime: false, expiryDate: '2026-11-10' }), '2026-11-10')).toBe(false)
  })
})
