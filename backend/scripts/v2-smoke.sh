#!/usr/bin/env bash
# Smoke test of the running v2 application (ClinicV2Application with the demo seed):
# logins and roles, reads, a discounted paid sale, a course purchase, a walk-in course usage and
# its reversal, appointments for CURRENT and NEW patients, and the reports.
# Needs curl and jq. BASE_URL defaults to http://localhost:8080.
set -uo pipefail

BASE="${BASE_URL:-http://localhost:8080}"
TOKEN=""
STATUS=""
BODY=""
failures=0

req() { # method path [json body]; the Authorization header is only sent when TOKEN is set
  local method="$1" path="$2" body="${3:-}" response
  local args=(-s -w '\n%{http_code}' -X "$method" "$BASE$path")
  if [ -n "$TOKEN" ]; then args+=(-H "Authorization: Bearer $TOKEN"); fi
  if [ -n "$body" ]; then args+=(-H 'Content-Type: application/json' -d "$body"); fi
  response=$(curl "${args[@]}")
  STATUS=$(printf '%s' "$response" | tail -n1)
  BODY=$(printf '%s' "$response" | sed '$d')
}

check() { # description condition-result(0=ok)
  if [ "$2" -eq 0 ]; then echo "ok   $1"; else echo "FAIL $1"; echo "     status=$STATUS body=$(printf '%s' "$BODY" | head -c 500)"; failures=$((failures + 1)); fi
}

expect_status() { [ "$STATUS" = "$2" ]; check "$1 (HTTP $2)" $?; }
expect_jq() { printf '%s' "$BODY" | jq -e "$2" > /dev/null 2>&1; check "$1" $?; }

login() { # username password
  TOKEN=""
  req POST /api/auth/login "$(jq -nc --arg u "$1" --arg p "$2" '{username:$u,password:$p}')"
  expect_status "login as $1" 200
  TOKEN=$(printf '%s' "$BODY" | jq -r '.token // empty')
  [ -n "$TOKEN" ] || { echo "no token for $1, stopping"; exit 1; }
}

echo "== security"
TOKEN="" req GET /api/branches
expect_status "a request without a token is rejected" 401
req POST /api/auth/login '{"username":"manager","password":"wrong-password"}'
[ "$STATUS" = "400" ] || [ "$STATUS" = "401" ] || [ "$STATUS" = "403" ]; check "a wrong password is rejected (HTTP $STATUS)" $?

login therapist therapist123
req GET /api/users
expect_status "a therapist cannot list users" 403
req POST /api/auth/register '{"username":"x","password":"password123"}'
expect_status "a therapist cannot register users" 403
req GET /api/branches
expect_status "a therapist can read branches" 200

login manager manager123
req GET /api/users
expect_status "a manager can list users" 200

echo "== reference and demo data"
req GET /api/branches;               expect_jq "two branches are seeded" 'length == 2'
req GET /api/staff;                  expect_status "staff list" 200
req GET /api/payment-methods;        expect_jq "four payment methods" 'length == 4'
req GET /api/commission/schemes;     expect_jq "the default commission scheme" 'length >= 1'
req GET /api/catalog/services;       expect_jq "single-visit services" 'map(.code) | index("PHYSIO_30") != null'
SERVICE_ID=$(printf '%s' "$BODY" | jq -r '.[] | select(.code=="PHYSIO_30") | .id')
req GET /api/catalog/courses;        expect_jq "courses" 'map(.code) | index("COURSE_10") != null'
COURSE_ID=$(printf '%s' "$BODY" | jq -r '.[] | select(.code=="COURSE_10") | .id')
req GET /api/catalog/therapists;     expect_jq "therapists" 'length >= 2'
THERAPIST_ID=$(printf '%s' "$BODY" | jq -r '.[0].id')
req GET '/api/patients?size=10';     expect_jq "three demo patients" '.items | length == 3'
PATIENT_ID=$(printf '%s' "$BODY" | jq -r '.items[0].id')

echo "== sale with a 10% line discount, paid in cash"
req POST /api/payments "$(jq -nc --argjson p "$PATIENT_ID" --argjson s "$SERVICE_ID" '{patientId:$p,branchCode:"01",
  items:[{type:"SERVICE",referenceId:$s,quantity:1,discountType:"PERCENT",discountValue:10}],
  payments:[{paymentMethodCode:"Cash",amount:720}]}')"
expect_status "create a sale" 201
expect_jq "receipt number is RE-xxxxxxx" '.receiptNo | test("^RE-[0-9]{7}$")'
expect_jq "total is 720 after the discount and fully paid" '.total == 720 and .paid == 720'
SALE_ID=$(printf '%s' "$BODY" | jq -r '.id')
req GET "/api/receipts/$SALE_ID";    expect_status "read the receipt" 200
req GET "/api/payments?patientId=$PATIENT_ID"; expect_jq "the sale is listed for the patient" '.items | length >= 1'

echo "== course purchase, walk-in usage and reversal"
req POST /api/course-purchases "$(jq -nc --argjson p "$PATIENT_ID" --argjson c "$COURSE_ID" \
  '{patientId:$p,courseId:$c,branchCode:"01",discountType:"BAHT",discountValue:500}')"
expect_status "buy a course with a 500 THB discount" 201
expect_jq "course price is 9500 and all 10 visits are left" '.price == 9500 and .remaining == 10'
PURCHASE_ID=$(printf '%s' "$BODY" | jq -r '.id')
req GET "/api/patients/$PATIENT_ID/courses"; expect_jq "the patient owns one course" 'length == 1'
req POST /api/course-usages "$(jq -nc --argjson c "$PURCHASE_ID" '{coursePurchaseId:$c,note:"smoke"}')"
expect_status "use one visit (walk-in)" 200
expect_jq "one visit used, nine left" '.used == 1 and .remaining == 9'
req GET "/api/course-purchases/$PURCHASE_ID"
MOVEMENT_ID=$(printf '%s' "$BODY" | jq -r '.movements[] | select(.type=="Treatment") | .id')
req POST "/api/course-movements/$MOVEMENT_ID/reverse" '{"reason":"smoke test"}'
expect_status "reverse the usage" 200
expect_jq "the visit is back" '.used == 0 and .remaining == 10'

echo "== appointments"
req POST /api/appointments "$(jq -nc --argjson p "$PATIENT_ID" --argjson t "$THERAPIST_ID" --argjson s "$SERVICE_ID" \
  '{branchCode:"01",patientId:$p,therapistId:$t,serviceId:$s,startAt:"2030-01-01T09:00:00+07:00"}')"
expect_status "book a registered patient" 201
APPOINTMENT_ID=$(printf '%s' "$BODY" | jq -r '.id')
req POST /api/appointments "$(jq -nc --argjson t "$THERAPIST_ID" \
  '{branchCode:"01",therapistId:$t,startAt:"2030-01-01T10:00:00+07:00",status:"Unconfirmed",patientType:"NEW",contactName:"สมศรี ใจงาม",contactPhone:"0899999999"}')"
expect_status "book a NEW patient as Unconfirmed" 201
expect_jq "the NEW appointment is Unconfirmed" '.status == "Unconfirmed" and .patientType == "NEW"'
NEW_APPOINTMENT_ID=$(printf '%s' "$BODY" | jq -r '.id')
req POST "/api/appointments/$NEW_APPOINTMENT_ID/confirm"; expect_jq "confirm moves it to Confirmed" '.status == "Confirmed"'
req POST "/api/appointments/$APPOINTMENT_ID/check-in";    expect_status "check in the registered patient" 200
req GET '/api/appointments?date=2030-01-01';              expect_jq "both appointments are listed" '.items | length == 2'

echo "== reports"
RANGE='from=2026-01-01&to=2030-12-31'
req GET "/api/dashboard/summary?$RANGE";       expect_status "dashboard" 200
req GET "/api/reports/revenue?$RANGE";         expect_status "revenue report" 200
req GET "/api/reports/appointments?$RANGE";    expect_status "appointment report" 200
req GET "/api/reports/course-balances?$RANGE"; expect_status "course balance report" 200
req GET "/api/reports/staff-sales?$RANGE";     expect_status "staff sales report" 200
req GET "/api/commission/report?$RANGE";       expect_status "commission report" 200

echo
if [ "$failures" -eq 0 ]; then echo "V2_SMOKE_OK"; else echo "V2_SMOKE_FAILED: $failures check(s)"; exit 1; fi
