const { test } = require('node:test')
const assert = require('node:assert/strict')
const { migrationFiles, compareManifests } = require('./rehearse.cjs')

test('includes future forward migrations in numeric Flyway order', () => {
  assert.deepEqual(migrationFiles(['V1002__future.sql', 'V3__seed.sql', 'notes.sql', 'V2_1__fix.sql', 'V2__base.sql']),
    ['V2__base.sql', 'V2_1__fix.sql', 'V3__seed.sql', 'V1002__future.sql'])
})

test('rejects duplicate Flyway versions instead of applying both', () => {
  assert.throws(() => migrationFiles(['V4__one.sql', 'V4_0__two.sql']), /Duplicate migration version/)
})

test('detects changed expiry even if counts and money match', () => {
  const first = { patients: 3, payments: '100.00', courses: [{ id: 1, expiry: '2027-01-05' }] }
  const second = { ...first, courses: [{ id: 1, expiry: '2027-01-06' }] }
  assert.throws(() => compareManifests(first, second), /not repeatable/)
  assert.doesNotThrow(() => compareManifests(first, structuredClone(first)))
})
