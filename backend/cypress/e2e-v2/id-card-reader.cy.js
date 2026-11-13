const validCard = {
  nationalId: '1101700005119', titleTh: 'นาย', firstNameTh: 'สมชาย', middleNameTh: null, lastNameTh: 'ใจดี',
  fullNameEn: 'Somchai Jaidee', birthDate: '1997-01-31', gender: 'M', address: '88/8 หมู่ที่ 3',
  issueDate: '2020-02-02', expiryDate: null, lifetime: true,
}

// The counter agent is played with plain requests: its own token in X-Agent-Token and no user JWT.
describe('ID card reader relay (agent simulated)', () => {
  let agent

  const asAgent = (method, url, body) =>
    cy.apiAs(null, method, url, body, { headers: { 'X-Agent-Token': agent.token } })

  beforeEach(() => {
    cy.api('POST', '/api/id-card/workstations', { name: `e2e ${Date.now()}`, branchCode: '01' }).then(({ body }) => {
      cy.apiAs(null, 'POST', '/api/id-card/agent/pair', { code: body.pairingCode }).then(({ body: paired }) => {
        agent = { token: paired.token, workstationId: body.id }
      })
    })
  })

  it('reads a card of a new patient: start, card in the reader, result, delivered once with the card', () => {
    asAgent('GET', '/api/id-card/agent/next?waitSeconds=0').its('status').should('equal', 204)
    cy.api('POST', '/api/id-card/reads', { workstationId: agent.workstationId, purpose: 'REGISTER' }).then(({ status, body }) => {
      expect(status).to.equal(202)
      asAgent('GET', '/api/id-card/agent/next?waitSeconds=3').then(({ body: command }) => {
        expect(command.requestId).to.equal(body.id)
        asAgent('POST', '/api/id-card/agent/progress', { requestId: body.id, stage: 'CARD_PRESENT' })
          .its('status').should('equal', 204)
        cy.api('GET', `/api/id-card/reads/${body.id}`).its('body.status').should('equal', 'READING')
        asAgent('POST', '/api/id-card/agent/result', { requestId: body.id, status: 'OK', card: validCard })
          .its('status').should('equal', 204)
      })
      cy.api('GET', `/api/id-card/reads/${body.id}`).then(({ body: done }) => {
        expect(done.status).to.equal('DONE')
        expect(done.lookup.exists).to.equal(false)
        expect(done.card.nationalId).to.equal(validCard.nationalId)
      })
      cy.api('GET', `/api/id-card/reads/${body.id}`).its('status').should('equal', 404)
    })
  })

  it('refuses a revoked reader', () => {
    cy.api('POST', `/api/id-card/workstations/${agent.workstationId}/revoke`).its('status').should('equal', 204)
    asAgent('GET', '/api/id-card/agent/next?waitSeconds=0').its('status').should('equal', 401)
  })
})
