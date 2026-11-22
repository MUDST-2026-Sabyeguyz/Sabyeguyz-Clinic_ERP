import { describe, expect, it } from 'vitest'
import { findDirectPatientCourse, resolveCourseRouteIds } from './courseRoute'

describe('course route identity', () => {
  it('uses direct path ids for patient and course', () => {
    expect(resolveCourseRouteIds({ patientId: '14', courseId: '92' }, {})).toEqual({
      patientId: 14,
      courseId: 92,
      fromPath: true,
    })
  })

  it('supports the existing query route when no path ids are present', () => {
    expect(resolveCourseRouteIds({}, { patientId: '14', courseId: '92' })).toEqual({
      patientId: 14,
      courseId: 92,
      fromPath: false,
    })
  })

  it('rejects conflicting path and query identities instead of selecting another patient', () => {
    expect(() =>
      resolveCourseRouteIds({ patientId: '14', courseId: '92' }, { patientId: '15', courseId: '92' }),
    ).toThrow('ข้อมูลผู้ป่วยหรือคอร์สในลิงก์ไม่ตรงกัน')
  })

  it('rejects incomplete or invalid direct paths', () => {
    expect(() => resolveCourseRouteIds({ patientId: '14', courseId: '' }, {})).toThrow(
      'ลิงก์ผู้ป่วยหรือคอร์สไม่ถูกต้อง',
    )
  })

  it('returns only a course included in the requested patient course list', () => {
    const rows = [{ id: 92, patient: { id: 14 } }, { id: 93, patient: { id: 15 } }]
    expect(findDirectPatientCourse(rows, 14, 92)).toBe(rows[0])
    expect(() => findDirectPatientCourse(rows, 14, 93)).toThrow('ไม่ได้เป็นสิทธิ์ของผู้ป่วย')
    expect(() => findDirectPatientCourse(rows, 14, 99)).toThrow('ไม่พบคอร์สนี้')
  })
})
