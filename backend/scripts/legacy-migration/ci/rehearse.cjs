// Cross-platform, disposable-DB rehearsal. Never drops or overwrites an existing database.
const { spawnSync } = require('node:child_process')
const { readFileSync, readdirSync, mkdirSync, writeFileSync } = require('node:fs')
const path = require('node:path')
const assert = require('node:assert/strict')

function migrationFiles(names) {
  const rows = names.flatMap(name => {
    const match = /^V(\d+(?:[._]\d+)*)__.*\.sql$/.exec(name)
    return match ? [{ name, version: match[1].split(/[._]/).map(Number) }] : []
  })
  const key = version => {
    const copy = [...version]
    while (copy.length > 1 && copy.at(-1) === 0) copy.pop()
    return copy.join('.')
  }
  const seen = new Set()
  for (const row of rows) {
    const value = key(row.version)
    if (seen.has(value)) throw new Error(`Duplicate migration version ${value}`)
    seen.add(value)
  }
  return rows.sort((a, b) => {
    for (let i = 0; i < Math.max(a.version.length, b.version.length); i++) {
      const delta = (a.version[i] || 0) - (b.version[i] || 0)
      if (delta) return delta
    }
    return 0
  }).map(row => row.name)
}

function compareManifests(first, second) {
  try { assert.deepStrictEqual(first, second) }
  catch (cause) { throw new Error('Migration is not repeatable from the same snapshot', { cause }) }
}

function main() {
  const args = process.argv.slice(2)
  if (args.length && !(args.length === 2 && args[0] === '--container')) {
    throw new Error('Usage: node scripts/legacy-migration/ci/rehearse.cjs [--container disposable-postgres-name]')
  }
  const container = args[1]
  const user = process.env.PGUSER || 'clinic'
  const root = path.resolve(__dirname, '../../..')
  const output = path.join(root, 'target', 'migration-rehearsal')
  mkdirSync(output, { recursive: true })
  const stamp = `${Date.now()}_${process.pid}`
  const databases = [`p9_${stamp}_source`, `p9_${stamp}_a`, `p9_${stamp}_b`]
  function run(program, cliArgs, input) {
    const command = container ? 'docker' : program
    const commandArgs = container ? ['exec', '-i', container, program, ...cliArgs] : cliArgs
    const result = spawnSync(command, commandArgs, { input, encoding: 'utf8', maxBuffer: 32 * 1024 * 1024 })
    if (result.error) throw result.error
    if (result.status !== 0) throw new Error(`${program} failed: ${(result.stderr || result.stdout).slice(-5000)}`)
    return result.stdout
  }
  function sql(db, input) { return run('psql', ['-X', '-qAt', '-v', 'ON_ERROR_STOP=1', '-U', user, '-d', db], input) }
  const file = relative => readFileSync(path.join(root, relative), 'utf8')
  const oldFolder = 'src/main/resources/db/migration_old'
  const newFolder = 'src/main/resources/db/migration'
  const oldFiles = migrationFiles(readdirSync(path.join(root, oldFolder)))
  const newFiles = migrationFiles(readdirSync(path.join(root, newFolder)))
  // CREATE DATABASE fails on collision; no DROP DATABASE or destructive schema operation on an existing DB.
  for (const db of databases) sql('postgres', `CREATE DATABASE "${db}";`)
  console.log('Created disposable databases:', databases.join(', '))
  console.log('Target migrations:', newFiles.join(', '))
  for (const name of oldFiles) sql(databases[0], file(`${oldFolder}/${name}`))
  sql(databases[0], file('scripts/legacy-migration/ci/legacy-fixture.sql'))
  const snapshot = run('pg_dump', ['-U', user, '-d', databases[0], '--no-owner', '--no-privileges'])
  const manifests = []
  const reconcile = file('scripts/legacy-migration/ci/reconcile.sql')
  for (const db of databases.slice(1)) {
    sql(db, snapshot)
    sql(db, 'ALTER SCHEMA public RENAME TO legacy; CREATE SCHEMA public;')
    for (const name of newFiles) sql(db, file(`${newFolder}/${name}`))
    sql(db, file('scripts/legacy-migration/migrate-legacy-to-v2.sql'))
    const manifest = JSON.parse(sql(db, reconcile).trim())
    manifests.push(manifest)
    writeFileSync(path.join(output, `${db}.json`), JSON.stringify(manifest, null, 2) + '\n')
    // Fault injection is rolled back when ON_ERROR_STOP terminates the connection.
    for (const [label, mutation] of [
      ['expiry', "UPDATE public.course SET expiry_date=expiry_date+1 WHERE course_id=(SELECT min(course_id) FROM public.course);"],
      ['receipt total', "UPDATE public.receipt SET net_amount=net_amount+1 WHERE receipt_id=(SELECT min(id) FROM legacy.sales);"],
      ['foreign key', "SET LOCAL session_replication_role=replica; UPDATE public.receipt SET patient_id=9223372036854775807 WHERE receipt_id=(SELECT min(id) FROM legacy.sales);"],
    ]) {
      let caught = false
      try { sql(db, `BEGIN; ${mutation}\n${reconcile}\nROLLBACK;`) }
      catch (failure) {
        if (!failure.message.includes('reconciliation failed')) throw failure
        caught = true
      }
      if (!caught) throw new Error(`Checker missed corrupted ${label}`)
      compareManifests(manifest, JSON.parse(sql(db, reconcile).trim()))
      console.log(`Detected and rolled back corrupted ${label} in ${db}`)
    }
    // The importer is intentionally not idempotent on populated targets; verify safe rejection.
    let rejected = false
    try { sql(db, file('scripts/legacy-migration/migrate-legacy-to-v2.sql')) }
    catch (failure) {
      if (!failure.message.includes('already contains data')) throw failure
      rejected = true
    }
    if (!rejected) throw new Error('Importer accepted a populated target')
    compareManifests(manifest, JSON.parse(sql(db, reconcile).trim()))
  }
  compareManifests(...manifests)
  const summary = { databases, migrations: newFiles, checks: manifests[0].checks, repeatable: true,
    sourceReceiptHeaderTotal: manifests[0].sourceReceiptHeaderTotal,
    receiptHeaderDifferences: manifests[0].receiptHeaderDifferences,
    corruptionDetected: ['expiry', 'receipt total', 'foreign key'], populatedTargetRejected: true,
    releaseReady: false, releaseNote: 'Technical rehearsal only; review header differences and unresolved business/schema gates before release.' }
  writeFileSync(path.join(output, 'summary.json'), JSON.stringify(summary, null, 2) + '\n')
  console.log('MIGRATION_REHEARSAL_OK', JSON.stringify(summary))
  console.log('Databases retained for inspection; artifacts:', output)
}

module.exports = { migrationFiles, compareManifests }
if (require.main === module) {
  try { main() } catch (failure) { console.error(failure.message); process.exitCode = 1 }
}
