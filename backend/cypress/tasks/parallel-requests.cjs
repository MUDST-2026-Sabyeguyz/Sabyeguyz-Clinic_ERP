// cy.request commands are queued serially. Start independent Node HTTP requests
// together so each request reaches its own Spring transaction while peers run.
module.exports = async function parallelRequests({ token, requests }) {
  if (!Array.isArray(requests) || requests.length !== 2) throw new Error('A race requires exactly two requests')
  for (const request of requests) {
    const allowedUrl =
        /^\/api\/[a-zA-Z0-9/-]+$/.test(request.url)
        || /^\/__cypress\/payment-policy\/\d+\/record$/.test(request.url)
        || /^\/api\/commission\/closings\/\d{4}-(0[1-9]|1[0-2])\?branchCode=[a-zA-Z0-9_-]+$/.test(request.url)

    if (!allowedUrl || !['POST', 'PATCH'].includes(request.method)) {
      throw new Error('Concurrency task only supports local API mutation routes')
    }
  }
  let release
  const start = new Promise((resolve) => { release = resolve })
  const jobs = requests.map(async ({ method, url, body }) => {
    await start
    const response = await fetch(`http://127.0.0.1:18080${url}`, {
      method,
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: AbortSignal.timeout(15000),
    })
    const text = await response.text()
    return { status: response.status, body: text ? JSON.parse(text) : null }
  })
  release()
  // Settle both requests before failing; otherwise the next reset can race a
  // still-running request and contaminate the following test's fixture state.
  const outcomes = await Promise.allSettled(jobs)
  const failure = outcomes.find((outcome) => outcome.status === 'rejected')
  if (failure) throw failure.reason
  return outcomes.map((outcome) => outcome.value)
}
