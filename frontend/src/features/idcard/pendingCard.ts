import type { IdCardData } from '@/api/client'

// Personal data from a card read, handed from the registration dialog to the registration form once.
// It is kept only in memory (never in the URL or in browser storage): a page reload forgets it and the card is read again.
let pending: IdCardData | null = null

export function setPendingCard(card: IdCardData): void {
  pending = card
}

export function takePendingCard(): IdCardData | null {
  const card = pending
  pending = null
  return card
}
