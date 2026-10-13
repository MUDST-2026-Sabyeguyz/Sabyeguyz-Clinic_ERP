#!/usr/bin/env bash
# Checks, through the HTTP API of the v2 app, that the migrated data is readable and that new records
# continue after it (HN, receipt numbers, sequences). Needs curl and jq.
# Expected numbers come from the environment (taken from the legacy tables by the workflow):
#   EXPECTED_PATIENTS EXPECTED_APPOINTMENTS COURSE1_ID COURSE1_REMAINING NEXT_RECEIPT_NO FX_PATIENT_ID
set -uo pipefail

BASE="${BASE_URL:-http://localhost:8080}"
TOKEN=""; STATUS=""; BODY=""; failures=0

req() {
  local method="$1" path="$2" body="${3:-}" response
  local args=(-s -w '\n%{http_code}' -X "$method" "$BASE$path")
  if [ -n "$TOKEN" ]; then args+=(-H "Authorization: Bearer $TOKEN"); fi
  if [ -n "$body" ]; then args+=(-H 'Content-Type: application/json' -d "$body"); fi
  response=$(curl "${args[@]}")
  STATUS=$(printf '%s' "$response" | tail -n1)
  BODY=$(printf '%s' "$response" | sed '$d')
}
check() { if [ "$2" -eq 0 ]; then echo "ok   $1"; else echo "FAIL $1"; echo "     status=$STATUS body=$(printf '%s' "$BODY" | head -c 600)"; failures=$((failures + 1)); fi; }
expect_status() { [ "$STATUS" = "$2" ]; check "$1 (HTTP $2)" $?; }
expect_jq() { printf '%s' "$BODY" | jq -e "$2" > /dev/null 2>&1; check "$1" $?; }

echo "== the old password still works"
req POST /api/auth/login '{"username":"manager","password":"manager123"}'
expect_status "login as manager with the password of the old system" 200
TOKEN=$(printf '%s' "$BODY" | jq -r '.token // empty')
[ -n "$TOKEN" ] || { echo "no token, stopping"; exit 1; }
expect_jq "the role came across" '.role == "MANAGER"'

echo "== migrated data is readable"
req GET '/api/patients?size=1'
expect_jq "all $EXPECTED_PATIENTS patients are there" ".totalItems == $EXPECTED_PATIENTS"
req GET "/api/patients/$FX_PATIENT_ID"
expect_jq "a patient keeps HN, national id and name" '.hn == "2601010001" and .identityNumber == "1103700000011" and (.fullName | startswith("สมชาย"))'
expect_jq "a patient keeps the customer group text" '.customerType == "VIP"'
req GET '/api/appointments?size=1'
expect_jq "all $EXPECTED_APPOINTMENTS appointments are there" ".totalItems == $EXPECTED_APPOINTMENTS"
req GET '/api/branches';       expect_jq "the branch came across" 'map(.branchCode) | index("01") != null'
req GET '/api/staff';          expect_jq "therapists became staff" 'length >= 3'
req GET '/api/payment-methods'; expect_jq "payment methods are there" 'length >= 4'
req GET '/api/commission/schemes'; expect_jq "the commission scheme and its tiers" 'length >= 1'
req GET "/api/course-purchases/$COURSE1_ID"
expect_jq "the course ledger balance is $COURSE1_REMAINING" ".purchase.remaining == $COURSE1_REMAINING"
expect_jq "the usage, its reversal and the transfer are in the ledger" '.movements | length == 5'
expect_jq "commission allocations (with the reversal row) are kept" '.allocations | length == 3'
expect_jq "the locked commission rate is kept" '(.purchase.lockedCommissionRate | tonumber) == 0.05'
req GET '/api/course-usages'; expect_jq "the usage list shows the reversed usage" 'map(select(.voidedAt != null)) | length == 1'
req GET '/api/reports/revenue?from=2020-01-01&to=2099-12-31'; expect_status "revenue report over migrated payments" 200
req GET '/api/dashboard/summary?from=2020-01-01&to=2099-12-31'; expect_status "dashboard over migrated data" 200

echo "== new records continue after the migrated ones"
req POST /api/patients '{"branchCode":"01","customerType":"Thai","title":"นาย","firstName":"หลัง","lastName":"ย้ายข้อมูล","gender":"Male","phone":"0899999999","identityDocumentType":"NATIONAL_ID","identityNumber":"1103700000037","birthDate":"1995-05-05","address":"x"}'
expect_status "register a patient after the migration" 201
NEW_PATIENT=$(printf '%s' "$BODY" | jq -r '.id')
expect_jq "the new HN has 10 digits" '.hn | test("^[0-9]{10}$")'
SERVICE_ID=$(curl -s -H "Authorization: Bearer $TOKEN" "$BASE/api/catalog/services" | jq -r '.[] | select(.code=="PHYSIO_30") | .id')
req POST /api/payments "$(jq -nc --argjson p "$NEW_PATIENT" --argjson s "$SERVICE_ID" '{patientId:$p,branchCode:"01",items:[{type:"SERVICE",referenceId:$s,quantity:1}],payments:[{paymentMethodCode:"Cash",amount:700}]}')"
expect_status "sell after the migration" 201
expect_jq "the receipt number continues at $NEXT_RECEIPT_NO" ".receiptNo == \"$NEXT_RECEIPT_NO\""
THERAPIST_ID=$(curl -s -H "Authorization: Bearer $TOKEN" "$BASE/api/catalog/therapists" | jq -r '.[0].id')
req POST /api/appointments "$(jq -nc --argjson p "$NEW_PATIENT" --argjson t "$THERAPIST_ID" --argjson s "$SERVICE_ID" '{branchCode:"01",patientId:$p,therapistId:$t,serviceId:$s,startAt:"2031-01-01T09:00:00+07:00"}')"
expect_status "book an appointment after the migration" 201

echo
if [ "$failures" -eq 0 ]; then echo "MIGRATION_VERIFIED"; else echo "MIGRATION_CHECK_FAILED: $failures check(s)"; exit 1; fi
