// Read-only GitHub inventory. Only annotations explicitly identifying billing are replayed.
const { execFileSync, execFile } = require('node:child_process')
const { promisify } = require('node:util')
const execute = promisify(execFile)
const fs = require('node:fs')
const path = require('node:path')
const repo = 'MUDST-2026-Sabyeguyz/Sabyeguyz-Clinic_ERP'
const output = path.join(__dirname, 'results')
fs.mkdirSync(output, { recursive: true })
function api(route) {
  for (let attempt = 0; ; attempt++) {
    try { return JSON.parse(execFileSync('gh', ['api', route], { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024, windowsHide: true })) }
    catch (error) { if (attempt >= 2) throw error }
  }
}
const runsFile = path.join(output, 'actions-runs.json')
const runs = fs.existsSync(runsFile) && !process.argv.includes('--refresh') ? JSON.parse(fs.readFileSync(runsFile, 'utf8')) : []
const needsFetch = !runs.length || process.argv.includes('--refresh')
for (let page = 1; needsFetch; page++) {
  const data = api(`repos/${repo}/actions/runs?per_page=100&page=${page}`)
  runs.push(...data.workflow_runs)
  console.log(`Inventory page ${page}: ${runs.length}/${data.total_count}`)
  if (data.workflow_runs.length < 100 || runs.length >= data.total_count) break
}
fs.writeFileSync(path.join(output, 'actions-runs.json'), JSON.stringify(runs, null, 2))
const billing = /(?:payments? have failed|spending limit|billing|exceeded.*(?:minutes|quota)|(?:minutes|quota).*exhausted)/i
const verified = [], other = [], errors = []
async function asyncApi(route) {
  for (let attempt = 0; ; attempt++) {
    try { return JSON.parse((await execute('gh', ['api', route], { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024, windowsHide: true })).stdout) }
    catch (error) { if (attempt >= 2) throw error }
  }
}
async function classify(run, index) {
  const cache = path.join(output, `annotation-${run.id}.json`)
  try {
    let annotations
    if (fs.existsSync(cache)) annotations = JSON.parse(fs.readFileSync(cache, 'utf8'))
    else {
      const checks = (await asyncApi(`repos/${repo}/check-suites/${run.check_suite_id}/check-runs?per_page=100`)).check_runs
      annotations = []
      for (const check of checks) {
        if (check.output.annotations_count) annotations.push(...await asyncApi(`repos/${repo}/check-runs/${check.id}/annotations?per_page=100`))
      }
      fs.writeFileSync(cache, JSON.stringify(annotations, null, 2))
    }
    const evidence = annotations.filter(a => billing.test(a.message)).map(a => a.message)
    if (evidence.length) verified.push({ id: run.id, sha: run.head_sha, branch: run.head_branch, workflow: run.path.split('@')[0], url: run.html_url, createdAt: run.created_at, evidence })
    else other.push({ id: run.id, sha: run.head_sha, workflow: run.path, messages: annotations.map(a => a.message) })
  } catch (error) { errors.push({ id: run.id, error: error.message }) }
  const commits = [...new Set(verified.map(r => r.sha))].map(sha => ({ sha, branches: [...new Set(verified.filter(r => r.sha === sha).map(r => r.branch))], workflows: [...new Set(verified.filter(r => r.sha === sha).map(r => r.workflow))], runs: verified.filter(r => r.sha === sha) }))
  fs.writeFileSync(path.join(output, 'replay-manifest.json'), JSON.stringify({ repository: repo, generatedAt: new Date().toISOString(), complete: false, verifiedRuns: verified.length, commits, other, errors }, null, 2))
  if (index % 10 === 0) console.log(`Classified ${index + 1}: billing=${verified.length}, other=${other.length}, errors=${errors.length}`)
}
async function main() {
const pending = runs.filter(r => r.conclusion === 'failure')
for (let offset = 0; offset < pending.length; offset += 4) {
  await Promise.all(pending.slice(offset, offset + 4).map((run, index) => classify(run, offset + index)))
}
const file = path.join(output, 'replay-manifest.json')
const manifest = JSON.parse(fs.readFileSync(file, 'utf8'))
manifest.complete = errors.length === 0
fs.writeFileSync(file, JSON.stringify(manifest, null, 2))
console.log(JSON.stringify({ complete: manifest.complete, commits: manifest.commits.length, billingRuns: verified.length, other: other.length, errors: errors.length }))
if (errors.length) process.exitCode = 1
}
main().catch(error => { console.error(error); process.exitCode = 1 })
