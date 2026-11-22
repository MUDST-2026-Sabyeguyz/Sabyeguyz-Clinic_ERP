// Jenkins API control. Credentials stay in the ignored, ACL-protected runtime folder.
const fs = require('node:fs')
const path = require('node:path')
const { execFileSync } = require('node:child_process')
const root = __dirname
const base = 'http://127.0.0.1:8086/'
const password = fs.readFileSync(path.join(root, 'runtime/admin-password'), 'utf8').trim()
const authorization = 'Basic ' + Buffer.from('clinic-admin:' + password).toString('base64')
let crumb
async function request(route, method = 'GET', body, asText = false) {
  const headers = { Authorization: authorization }
  if (method === 'POST') {
    if (!crumb) crumb = await request('crumbIssuer/api/json')
    headers[crumb.crumbRequestField] = crumb.crumb
    headers.Cookie = crumb.cookie || ''
    headers['Content-Type'] = 'application/x-www-form-urlencoded'
  }
  const response = await fetch(base + route, { method, headers, body })
  if (!response.ok) throw Error(`${method} ${route}: ${response.status}`)
  if (method === 'POST') return asText ? response.text() : { location: response.headers.get('location') }
  const data = await response.json()
  if (route === 'crumbIssuer/api/json') data.cookie = response.headers.getSetCookie().map(c => c.split(';')[0]).join('; ')
  return data
}
async function enqueue(sha, workflows = '') {
  if (!/^[a-f0-9]{40}$/.test(sha)) throw Error('Expected full SHA')
  return request('job/Clinic-CI/buildWithParameters', 'POST', new URLSearchParams({ SOURCE_SHA: sha, WORKFLOWS: workflows }).toString())
}
const indexFile = path.join(root, 'results/jenkins-builds.json')
async function main(override) {
  const action = override || process.argv[2] || 'status'
  if (action === 'start') {
    const sha = execFileSync('git', ['rev-parse', 'origin/develop'], { encoding: 'utf8' }).trim()
    const entry = { sha, workflows: [], ...await enqueue(sha), queuedAt: new Date().toISOString() }
    const builds = fs.existsSync(indexFile) ? JSON.parse(fs.readFileSync(indexFile, 'utf8')).filter(build => build.sha !== sha) : []
    builds.push(entry)
    fs.writeFileSync(indexFile, JSON.stringify(builds, null, 2))
    console.log(JSON.stringify(entry))
  } else if (action === 'lint') {
    for (const name of ['Replay.Jenkinsfile', 'Watch.Jenkinsfile']) {
      const data = new URLSearchParams({ jenkinsfile: fs.readFileSync(path.join(root, name), 'utf8') }).toString()
      const result = await request('pipeline-model-converter/validate', 'POST', data, true)
      console.log(`${name}: ${result.trim()}`)
      if (!result.includes('Jenkinsfile successfully validated')) throw Error('Pipeline validation failed')
    }
  } else if (action === 'reload') {
    const script = fs.readFileSync(path.join(root, 'seed-ci.groovy'), 'utf8')
    console.log(JSON.stringify(await request('scriptText', 'POST', new URLSearchParams({ script }).toString())))
  } else if (action === 'stop') {
    const number = process.argv[3]
    if (!/^\d+$/.test(number || '')) throw Error('Expected a build number')
    console.log(JSON.stringify(await request(`job/Clinic-CI/${number}/stop`, 'POST', '')))
  } else if (action === 'watch') {
    console.log(JSON.stringify(await request('job/Clinic-Watch/build', 'POST', '')))
  } else if (action === 'enqueue') {
    const manifest = JSON.parse(fs.readFileSync(path.join(root, 'results/replay-manifest.json'), 'utf8'))
    if (!manifest.complete) throw Error('Inventory must complete without API errors before bulk scheduling')
    const builds = fs.existsSync(indexFile) ? [...new Map(JSON.parse(fs.readFileSync(indexFile, 'utf8')).map(build => [build.sha, build])).values()] : []
    fs.writeFileSync(indexFile, JSON.stringify(builds, null, 2))
    for (const commit of manifest.commits) {
      if (builds.some(build => build.sha === commit.sha)) continue
      try { execFileSync('git', ['cat-file', '-e', `${commit.sha}^{commit}`], { stdio: 'ignore' }) }
      catch { execFileSync('git', ['fetch', 'origin', commit.sha], { stdio: 'inherit' }); throw Error('Fetched missing commit; refresh source bundle and retry') }
      const queued = await enqueue(commit.sha, commit.workflows.join(','))
      builds.push({ sha: commit.sha, workflows: commit.workflows, ...queued, queuedAt: new Date().toISOString() })
      fs.writeFileSync(indexFile, JSON.stringify(builds, null, 2))
      console.log(`${builds.length}/${manifest.commits.length}: ${commit.sha.slice(0, 12)}`)
    }
  } else if (action === 'report') {
    const manifest = JSON.parse(fs.readFileSync(path.join(root, 'results/replay-manifest.json'), 'utf8'))
    const job = await request('job/Clinic-CI/api/json?tree=builds[number,result,building,url,actions[parameters[name,value]]]')
    const queue = await request('queue/api/json?tree=items[id,params]')
    const rows = [], candidates = []
    for (const commit of manifest.commits) {
      const build = job.builds.find(build => build.actions.some(action => action.parameters?.some(p => p.name === 'SOURCE_SHA' && p.value === commit.sha)))
      let summary
      if (build && !build.building) {
        try { summary = await request(`job/Clinic-CI/${build.number}/artifact/results/${build.number}/summary.json`) } catch {}
      }
      const queued = queue.items.some(item => item.params?.includes('SOURCE_SHA=' + commit.sha))
      const state = build?.building ? 'RUNNING' : queued ? 'QUEUED' : build ? build.result : 'NOT_QUEUED'
      rows.push({ sha: commit.sha, branches: commit.branches, state, build: queued && !build?.building ? undefined : build?.number,
        url: queued && !build?.building ? undefined : build?.url, summary })
      if (state === 'SUCCESS' && summary?.sha === commit.sha && summary.result === 'success') {
        for (const run of commit.runs) {
          const jobs = summary.jobs.filter(job => job.workflow === run.workflow)
          if (jobs.some(job => job.result === 'success') && jobs.every(job => ['success', 'skipped'].includes(job.result))) {
            candidates.push({ runId: run.id, sha: commit.sha, workflow: run.workflow, jenkinsBuild: build.number, evidence: `${build.url}artifact/results/${build.number}/summary.json` })
          }
        }
      }
    }
    const counts = rows.reduce((counts, row) => { counts[row.state] = (counts[row.state] || 0) + 1; return counts }, {})
    const markdown = [ '# Jenkins replay results', '', `Updated: ${new Date().toISOString()}`, '',
      `${manifest.verifiedRuns} billing-blocked runs; ${manifest.commits.length} unique commits. ${JSON.stringify(counts)}`, '',
      'These are Jenkins results for original workflow commands. They do not change old Actions results. Tool differences are recorded in each summary artifact.', '',
      '| Commit | Branch | Result | Jenkins |', '| --- | --- | --- | --- |',
      ...rows.map(row => `| ${row.sha.slice(0,12)} | ${row.branches.join(', ')} | ${row.state} | ${row.url ? `[Build #${row.build}](${row.url})` : 'Pending'} |`), '',
      `${candidates.length} Actions runs have matching successful Jenkins evidence and can be reviewed for deletion. Nothing was deleted.`, '' ].join('\n')
    fs.writeFileSync(path.join(root, 'results/replay-report.md'), markdown)
    fs.writeFileSync(path.join(root, 'results/replay-results.json'), JSON.stringify(rows, null, 2))
    fs.writeFileSync(path.join(root, 'results/deletion-candidates.json'), JSON.stringify(candidates, null, 2))
    const result = { commits: rows.length, counts, deletionCandidates: candidates.length }
    if (!override) console.log(JSON.stringify(result))
    return result
  } else if (action === 'monitor') {
    let previous
    while (true) {
      try {
        const result = await main('report')
        const current = JSON.stringify(result)
        if (current !== previous) { console.log(current); previous = current }
        if (!['RUNNING', 'QUEUED', 'NOT_QUEUED'].some(state => result.counts[state])) break
      } catch (error) { console.error('Report retry: ' + error.message) }
      await new Promise(resolve => setTimeout(resolve, 30000))
    }
  } else if (action === 'status') {
    const jobs = await request('job/Clinic-CI/api/json?tree=builds[number,result,building,url,actions[parameters[name,value]]],queueItem[id]')
    const queue = await request('queue/api/json?tree=items[id,why,params]')
    console.log(JSON.stringify({ builds: jobs.builds.map(build => ({ number: build.number, result: build.building ? 'RUNNING' : build.result,
      sha: build.actions.flatMap(action => action.parameters || []).find(p => p.name === 'SOURCE_SHA')?.value, url: build.url })), queued: queue.items.length }, null, 2))
  } else throw Error('Usage: node control.cjs start|enqueue|status|report|monitor|watch|lint|reload|stop NUMBER')
}
main().catch(error => { console.error(error.message); process.exitCode = 1 })
