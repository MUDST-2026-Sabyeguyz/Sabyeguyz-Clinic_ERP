#!/usr/bin/env bash
# Checks migration V1002 (commission schema) against a throw-away PostgreSQL 16 in Docker:
#   1) a brand new database      (V1, V2, V3, V1001, V1002, V1003)
#   2) a database with the demo seed (V1, V2, V3, V1000, V1001, V1002, V1003)
#   3) an upgrade of a database that already holds old payment/closing rows (status conversions, NOT VALID report)
# Each run applies V1002 in ONE transaction, like Flyway does. Needs Docker; nothing outside the container is touched.
#
#   bash backend/scripts/commission-migration/check-v1002.sh
#
# The check with realistic data (courses, usages, ledgers made by the old code) is manual: run the backend with
# SPRING_FLYWAY_TARGET=1001, create data with seed-old-data.mjs, then apply V1002 (see docs/commission-schema-migration.md).
set -u
root="$(cd "$(dirname "$0")/../.." && pwd)" # the backend folder
mig="$root/src/main/resources/db"
name="sabyeguyz-v1002-check"
pass=0; fail=0
ok() { echo "  PASS  $*"; pass=$((pass + 1)); }
bad() { echo "  FAIL  $*"; fail=$((fail + 1)); }

for f in "$mig/migration/V1__initial_schema.sql" "$mig/migration/V2__integration_runtime_compatibility.sql" \
         "$mig/migration/V3__reference_data.sql" "$mig/migration/V1001__historical_assessment_transaction_type.sql" \
         "$mig/migration/V1002__implement_commission_requirements.sql" "$mig/migration/V1003__commission_tier_contiguity.sql" \
         "$mig/seed_v2/V1000__demo_data.sql"; do
  [ -f "$f" ] || { echo "missing migration file: $f"; exit 1; }
done
command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1 || { echo "Docker is not running."; exit 1; }
cleanup() { docker rm -f "$name" >/dev/null 2>&1; }
trap cleanup EXIT
docker rm -f "$name" >/dev/null 2>&1
docker run -d --name "$name" -e POSTGRES_USER=check -e POSTGRES_PASSWORD=check -e POSTGRES_DB=postgres postgres:16-alpine >/dev/null || exit 1
for _ in $(seq 1 30); do docker exec "$name" pg_isready -U check >/dev/null 2>&1 && break; sleep 1; done
sleep 2

psql_i() { docker exec -i "$name" psql -U check "$@"; }
newdb() { psql_i -d postgres -q -c "DROP DATABASE IF EXISTS $1" -c "CREATE DATABASE $1" 2>&1 | grep -v NOTICE; }
apply() { # db file: one transaction; prints the output and returns psql's exit status
  psql_i -d "$1" --single-transaction -v ON_ERROR_STOP=1 -q < "$2" 2>&1
  return "${PIPESTATUS[0]}"
}
scalar() { psql_i -d "$1" -At -c "$2" 2>&1; }
files_old() { echo "$mig/migration/V1__initial_schema.sql $mig/migration/V2__integration_runtime_compatibility.sql $mig/migration/V3__reference_data.sql"; }
V1002="$mig/migration/V1002__implement_commission_requirements.sql"
V1001="$mig/migration/V1001__historical_assessment_transaction_type.sql"
V1003="$mig/migration/V1003__commission_tier_contiguity.sql"
SEED="$mig/seed_v2/V1000__demo_data.sql"

structure() { # db label
  local db=$1 label=$2
  [ "$(scalar "$db" "select count(*) from information_schema.tables where table_name in ('course_refund_item','single_visit_fee_ledger','staff_pay_adjustment','adjustment_application')")" = "4" ] \
    && ok "$label: four new tables exist" || bad "$label: new tables missing"
  [ "$(scalar "$db" "select string_agg(enumlabel, ',' order by enumsortorder) from pg_enum e join pg_type t on t.oid=e.enumtypid where t.typname='closing_status_enum'")" = "Open,Closed,Finalized" ] \
    && ok "$label: closing_status_enum is Open,Closed,Finalized" || bad "$label: closing_status_enum wrong"
  [ "$(scalar "$db" "select count(*) from information_schema.columns where (table_name='course' and column_name in ('case_owner_staff_id','gross_commission_allocated_total','entitled_visits')) or (table_name='course_visit_usage' and column_name in ('case_owner_staff_id','gross_commission_allocation','print_required'))")" = "0" ] \
    && ok "$label: obsolete columns are gone" || bad "$label: obsolete columns remain"
  [ "$(scalar "$db" "select count(*) from information_schema.columns where is_nullable='YES' and ((table_name='course' and column_name='treatment_fee_base_amount') or (table_name='course_visit_usage' and column_name='usage_sequence') or (table_name='commission_allocation_ledger' and column_name='allocation_version'))")" = "0" ] \
    && ok "$label: backfilled columns are NOT NULL" || bad "$label: backfilled columns are nullable"
}

echo "1) brand new database"
newdb chk_new
for f in $(files_old) "$V1001" "$V1002" "$V1003"; do out=$(apply chk_new "$f") || bad "new: $(basename "$f"): $out"; done
structure chk_new "new"
[ "$(scalar chk_new "select count(*) from pg_constraint where not convalidated")" = "0" ] && ok "new: every constraint is validated" || bad "new: NOT VALID constraints left"

echo "2) database with the demo seed"
newdb chk_seed
for f in $(files_old) "$SEED" "$V1001" "$V1002" "$V1003"; do out=$(apply chk_seed "$f") || bad "seed: $(basename "$f"): $out"; done
structure chk_seed "seed"
[ "$(scalar chk_seed "select count(*) from commission_tier t join commission_tier n on n.commission_scheme_id = t.commission_scheme_id and n.tier_order = t.tier_order + 1 where n.minimum_monthly_sales <> t.maximum_monthly_sales + 0.01")" = "0" ] \
  && ok "seed: tier ranges are contiguous at two decimals (V1003)" || bad "seed: a tier gap remains"

echo "3) upgrade with old payment and closing rows"
newdb chk_old
for f in $(files_old) "$SEED" "$V1001"; do apply chk_old "$f" >/dev/null || bad "old: setup $(basename "$f")"; done
psql_i -d chk_old -q -v ON_ERROR_STOP=1 <<'SQL'
INSERT INTO staff_monthly_payment (staff_id, payment_month, payment_status, paid_at, status_changed_by, status_changed_at)
VALUES (1, '2026-08', 'Unpaid', NULL, 1, now()), (2, '2026-08', 'Paid', now(), 1, now());
SQL
out=$(apply chk_old "$V1002") && ok "old: migration succeeds on old rows" || bad "old: $out"
[ "$(scalar chk_old "select payment_status from staff_monthly_payment where staff_id=1")" = "Open" ] && ok "old: Unpaid became Open" || bad "old: Unpaid not converted"
[ "$(scalar chk_old "select payment_status from staff_monthly_payment where staff_id=2")" = "Paid" ] && ok "old: Paid stays Paid" || bad "old: Paid changed"
echo "$out" | grep -q "chk_staff_monthly_payment_amounts" && ok "old: a Paid row without amounts is reported (constraint left NOT VALID)" || bad "old: expected a NOTICE for chk_staff_monthly_payment_amounts"
[ "$(scalar chk_old "select count(*) from pg_constraint where conname='chk_staff_monthly_payment_amounts' and not convalidated")" = "1" ] && ok "old: that constraint is enforced for new rows" || bad "old: constraint state wrong"

echo
echo "passed: $pass, failed: $fail"
[ "$fail" = "0" ] && echo "ALL CHECKS PASSED" || echo "SOME CHECKS FAILED"
exit "$fail"
