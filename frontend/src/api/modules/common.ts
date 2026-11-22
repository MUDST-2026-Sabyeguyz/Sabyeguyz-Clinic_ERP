export type Page<T> = {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export type CancellationReasonCode =
  | 'WrongEntryOrService'
  | 'DuplicateEntry'
  | 'PatientRequested'
  | 'PaymentFailedOrIncorrectAmount'
  | 'Other'

export type CancellationRequest = {
  reasonCode: CancellationReasonCode
  reason: string
}
