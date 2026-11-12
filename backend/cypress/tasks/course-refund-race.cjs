// Hold the production usage transaction, observe a PostgreSQL lock wait, then release it.
module.exports = async function courseRefundRace({token, courseId, appointmentId, refundUrl, refundBody}) {
  const testPort = Number(process.env.CLINIC_V2_TEST_PORT || 18080)
  if (![18080, 18082].includes(testPort)) throw new Error('Only isolated test ports are supported')
  if (!Number.isSafeInteger(courseId)
      || !Number.isSafeInteger(appointmentId)
      || !(/^\/api\/payments\/\d+\/refunds$/.test(refundUrl)
          || /^\/api\/refunds\/\d+\/complete$/.test(refundUrl))) {
    throw new Error('Only isolated course/refund routes are supported')
  }
  const call = async (method, url, body) => {
    const response = await fetch(`http://127.0.0.1:${testPort}${url}`, {
      method, headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(25000),
    })
    return { status: response.status, body: await response.json() }
  }
  const route = `/__cypress/payment-policy/held-usage/${courseId}`
  // Observe rejection immediately while polling, and always settle started HTTP jobs.
  const observe = promise => promise.then(value => ({ value }), error => ({ error }))
  const usage = observe(call(
      'POST',
      `${route}?appointmentId=${appointmentId}`
  ))
  let refund
  let settled = false
  let observedWait = false
  let setupError
  let releaseError
  try {
    const deadline = Date.now() + 10000
    while (true) {
      if ((await call('GET', route)).body.held) break
      if (Date.now() > deadline) throw new Error('Usage did not reach the hold barrier')
      await new Promise(resolve => setTimeout(resolve, 50))
    }
    refund = observe(call('POST', refundUrl, refundBody)).then(outcome => { settled = true; return outcome })
    while (!settled && Date.now() < deadline) {
      if ((await call('GET', route)).body.blocked) { observedWait = true; break }
      await new Promise(resolve => setTimeout(resolve, 50))
    }
  } catch (error) {
    setupError = error
  } finally {
    try { await call('POST', `${route}/release`) } catch (error) { releaseError = error }
  }
  const outcomes = await Promise.all([usage, refund])
  const failure = outcomes.find(outcome => outcome?.error)
  if (setupError) throw setupError
  if (releaseError) throw releaseError
  if (failure) throw failure.error
  return {
    observedWait,
    usage: outcomes[0].value,
    refund: outcomes[1].value
  }
}
