export type CourseRouteIds = { patientId: number; courseId: number; fromPath: boolean }

function positiveId(value: unknown): number | null {
  if (typeof value !== 'string' || !/^\d+$/.test(value)) return null
  const id = Number(value)
  return Number.isSafeInteger(id) && id > 0 ? id : null
}

/** Direct path identifiers are authoritative; conflicting legacy query IDs are rejected. */
export function resolveCourseRouteIds(
  params: Record<string, unknown>,
  query: Record<string, unknown>,
): CourseRouteIds {
  const pathPatient = positiveId(params.patientId)
  const pathCourse = positiveId(params.courseId)
  const hasPath = params.patientId != null || params.courseId != null
  if (hasPath) {
    if (!pathPatient || !pathCourse) throw new Error('ลิงก์ผู้ป่วยหรือคอร์สไม่ถูกต้อง')
    const queryPatient = positiveId(query.patientId)
    const queryCourse = positiveId(query.courseId)
    if (
      (query.patientId != null && queryPatient !== pathPatient) ||
      (query.courseId != null && queryCourse !== pathCourse)
    ) throw new Error('ข้อมูลผู้ป่วยหรือคอร์สในลิงก์ไม่ตรงกัน กรุณาเปิดลิงก์ใหม่')
    return { patientId: pathPatient, courseId: pathCourse, fromPath: true }
  }

  const patientId = positiveId(query.patientId)
  if (!patientId) throw new Error('กรุณาระบุผู้ป่วย')
  const courseId = positiveId(query.courseId)
  return { patientId, courseId: courseId ?? 0, fromPath: false }
}

export function findDirectPatientCourse<T extends { id: number; patient: { id: number } }>(
  courses: T[],
  patientId: number,
  courseId: number,
): T {
  const course = courses.find((item) => item.id === courseId)
  if (!course || course.patient.id !== patientId)
    throw new Error('ไม่พบคอร์สนี้หรือคอร์สไม่ได้เป็นสิทธิ์ของผู้ป่วยที่ระบุ')
  return course
}
