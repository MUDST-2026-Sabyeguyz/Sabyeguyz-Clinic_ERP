describe('API contracts', () => {
  it('returns the initial hello contract', () => {
    cy.api('GET', '/api/hello').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body).to.include({ message: 'Hello from Physiotherapy Clinic ERP', service: 'backend' })
    })
  })

  it('exposes Swagger UI and the patient OpenAPI contract', () => {
    cy.request('/swagger-ui/index.html').its('status').should('equal', 200)
    cy.request('/v3/api-docs').then(({ body }) => {
      const patients = body.paths['/api/patients']
      const profile = body.paths['/api/patients/{id}'].get
      expect(patients.post.tags[0]).to.equal('Patients')
      expect(patients.get.tags[0]).to.equal('Patients')
      expect(profile.tags[0]).to.equal('Patients')
      expect(patients.get.parameters).to.have.length(5)
      expect(patients.get.parameters.map(({ name }) => name)).to.include('sort')
      expect(patients.get.responses['400'].content['application/json'].schema).to.exist
      expect(profile.responses['404'].content['application/json'].schema).to.exist
      for (const schema of ['PatientRegistrationRequest', 'PatientSearchResponse', 'PatientProfileResponse', 'ApiErrorResponse']) {
        expect(body.components.schemas).to.have.property(schema)
      }
    })
  })

  it('documents appointment creation and conflicts', () => {
    cy.request('/v3/api-docs').then(({ body }) => {
      const create = body.paths['/api/appointments'].post
      expect(create.tags[0]).to.equal('Appointments')
      expect(create.responses['409'].content['application/json'].schema).to.exist
      expect(body.components.schemas).to.have.property('AppointmentCreateRequest')
      expect(body.components.schemas).to.have.property('AppointmentResponse')
    })
  })
})
