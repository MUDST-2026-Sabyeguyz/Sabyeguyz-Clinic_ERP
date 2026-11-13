const nameDigits = /[0-9\u0e50-\u0e59]/g

export function sanitizePatientName(value: string) {
  return value.replace(nameDigits, '')
}
