import { patientApi, type Patient, type PatientRegistrationRequest } from './client'

export type PatientRegistrationPayload = PatientRegistrationRequest

export type PatientRegistrationResult = Patient

export function registerPatient(payload: PatientRegistrationPayload) {
  return patientApi.register(payload)
}
