#!/usr/bin/env bash
# Checks the Google Calendar settings WITHOUT printing any secret: can we sign in, can we see each branch
# calendar, can we write and remove an event. Run it on the machine that will run the server.
#
# Recommended: a service account. Put the path of its JSON key file in .env.calendar (repository root, git-ignored):
#        APP_CALENDAR_SERVICE_ACCOUNT_KEY_FILE=/path/to/calendar-sync-key.json
# (needs openssl and curl). Older alternative: the OAuth refresh token of a Gmail account:
#        APP_CALENDAR_CLIENT_ID=...   APP_CALENDAR_CLIENT_SECRET=...   APP_CALENDAR_REFRESH_TOKEN=...
#
#   bash backend/scripts/calendar-check.sh <calendarId> [<calendarId> ...]
#
# The values can also come from environment variables of the same names.
set -u
root="$(cd "$(dirname "$0")/../.." && pwd)"
if [ -f "$root/.env.calendar" ]; then
  set -a; . "$root/.env.calendar"; set +a
fi
keyfile="${APP_CALENDAR_SERVICE_ACCOUNT_KEY_FILE:-}"
if [ -z "$keyfile" ]; then
  : "${APP_CALENDAR_CLIENT_ID:?set APP_CALENDAR_SERVICE_ACCOUNT_KEY_FILE (or the three OAuth values; see the comment at the top of this script)}"
  : "${APP_CALENDAR_CLIENT_SECRET:?APP_CALENDAR_CLIENT_SECRET is not set}"
  : "${APP_CALENDAR_REFRESH_TOKEN:?APP_CALENDAR_REFRESH_TOKEN is not set}"
fi
[ "$#" -ge 1 ] || { echo "usage: bash backend/scripts/calendar-check.sh <calendarId> [<calendarId> ...]"; exit 2; }

api="https://www.googleapis.com/calendar/v3"
fail=0
say() { printf '%s\n' "$*"; }
field() { sed -n "s/.*\"$1\" *: *\"\([^\"]*\)\".*/\1/p" | head -1; }
b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }
keyvalue() { # $1 = field of the JSON key file (plain text parsing; the key file is one flat object)
  grep -o "\"$1\" *: *\"[^\"]*\"" "$keyfile" | head -1 | sed 's/^[^:]*: *"//; s/"$//' | { read -r v; printf '%b\n' "$v"; }
}

if [ -n "$keyfile" ]; then
  say "1) Signing in with the service account key file ..."
  [ -f "$keyfile" ] || { say "   FAILED: the key file does not exist: $keyfile"; exit 1; }
  email=$(keyvalue client_email); uri=$(keyvalue token_uri); uri="${uri:-https://oauth2.googleapis.com/token}"
  tmpkey=$(mktemp); trap 'rm -f "$tmpkey"' EXIT; chmod 600 "$tmpkey"; keyvalue private_key > "$tmpkey"
  now=$(date +%s)
  unsigned="$(printf '{"alg":"RS256","typ":"JWT"}' | b64url).$(printf '{"iss":"%s","scope":"https://www.googleapis.com/auth/calendar","aud":"%s","iat":%s,"exp":%s}' "$email" "$uri" "$now" "$((now + 3600))" | b64url)"
  jwt="$unsigned.$(printf '%s' "$unsigned" | openssl dgst -sha256 -sign "$tmpkey" -binary | b64url)"
  reply=$(curl -s -m 30 -w '\n%{http_code}' "$uri" \
    --data-urlencode "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer" --data-urlencode "assertion=$jwt")
  say "   service account: $email"
else
  say "1) Signing in with the refresh token ..."
  reply=$(curl -s -m 30 -w '\n%{http_code}' https://oauth2.googleapis.com/token \
    --data-urlencode "client_id=$APP_CALENDAR_CLIENT_ID" --data-urlencode "client_secret=$APP_CALENDAR_CLIENT_SECRET" \
    --data-urlencode "refresh_token=$APP_CALENDAR_REFRESH_TOKEN" --data-urlencode "grant_type=refresh_token")
fi
code=$(printf '%s' "$reply" | tail -1); body=$(printf '%s' "$reply" | sed '$d')
if [ "$code" != "200" ]; then
  why=$(printf '%s' "$body" | field error)
  say "   FAILED (HTTP $code${why:+, $why})"
  case "$why" in
    invalid_client) say "   -> the Client ID or Client secret is wrong (check for extra spaces or a missing character)." ;;
    invalid_grant)  say "   -> the refresh token or the service account key is wrong, expired or revoked (an OAuth app in Testing status expires after 7 days; a service account key does not)." ;;
    *) say "   -> no answer from Google? check the internet connection of this machine." ;;
  esac
  exit 1
fi
token=$(printf '%s' "$body" | field access_token)
say "   OK"

enc() { printf '%s' "$1" | sed 's/@/%40/g'; }
for cal in "$@"; do
  say "2) Calendar $(printf '%s' "$cal" | cut -c1-12)... "
  info=$(curl -s -m 30 -w '\n%{http_code}' -H "Authorization: Bearer $token" "$api/calendars/$(enc "$cal")")
  code=$(printf '%s' "$info" | tail -1)
  if [ "$code" != "200" ]; then
    say "   cannot open it (HTTP $code) -> wrong Calendar ID, or the account does not own or share it with the service account e-mail."; fail=1; continue
  fi
  say "   OK: $(printf '%s' "$info" | sed '$d' | field summary)"
  start=$(date -u -d '+1 day' +%Y-%m-%dT03:00:00Z 2>/dev/null || date -u -v+1d +%Y-%m-%dT03:00:00Z)
  end=$(date -u -d '+1 day' +%Y-%m-%dT03:30:00Z 2>/dev/null || date -u -v+1d +%Y-%m-%dT03:30:00Z)
  made=$(curl -s -m 30 -w '\n%{http_code}' -X POST -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
    -d "{\"summary\":\"[test] Clinic ERP calendar check\",\"start\":{\"dateTime\":\"$start\"},\"end\":{\"dateTime\":\"$end\"}}" \
    "$api/calendars/$(enc "$cal")/events")
  code=$(printf '%s' "$made" | tail -1); id=$(printf '%s' "$made" | sed '$d' | field id)
  if [ "$code" != "200" ] || [ -z "$id" ]; then
    say "   cannot write an event (HTTP $code) -> share the calendar with the account and choose "Make changes to events"."; fail=1; continue
  fi
  del=$(curl -s -m 30 -o /dev/null -w '%{http_code}' -X DELETE -H "Authorization: Bearer $token" "$api/calendars/$(enc "$cal")/events/$id")
  if [ "$del" = "204" ] || [ "$del" = "200" ]; then say "   OK: wrote and removed a test event."; else say "   wrote the test event but could not remove it (HTTP $del); delete '[test] Clinic ERP calendar check' by hand."; fail=1; fi
done

if [ "$fail" = "0" ]; then say "ALL GOOD: the settings work."; else say "Some checks failed (see above)."; fi
exit "$fail"
