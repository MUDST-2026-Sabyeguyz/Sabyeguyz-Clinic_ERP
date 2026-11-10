import { describe, expect, it } from 'vitest'
import type { IdCardData } from '@/api/client'
import { setPendingCard, takePendingCard } from './pendingCard'

const card = { nationalId: '1101700005119' } as IdCardData

describe('pendingCard', () => {
  it('hands the card over once and then forgets it', () => {
    expect(takePendingCard()).toBeNull()
    setPendingCard(card)
    expect(takePendingCard()).toEqual(card)
    expect(takePendingCard()).toBeNull()
  })

  it('keeps only the latest card', () => {
    setPendingCard({ nationalId: '1' } as IdCardData)
    setPendingCard(card)
    expect(takePendingCard()).toEqual(card)
  })

  it('never writes the card to browser storage', () => {
    setPendingCard(card)
    expect(JSON.stringify({ ...localStorage })).not.toContain('1101700005119')
    expect(JSON.stringify({ ...sessionStorage })).not.toContain('1101700005119')
    takePendingCard()
  })
})
