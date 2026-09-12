export interface PatientCourse {
  name: string
  balance: number
  total: number
  startDate: string
  expiry: string
}

export const patientCourses: PatientCourse[] = [
  {
    name: 'Manual Therapy 10 ครั้ง',
    balance: 6,
    total: 10,
    startDate: '22 ส.ค. 2026',
    expiry: '30 ธันวาคม 2026',
  },
  {
    name: 'Exercise Therapy 6 ครั้ง',
    balance: 4,
    total: 6,
    startDate: '15 ม.ค. 2026',
    expiry: '15 มกราคม 2027',
  },
]
