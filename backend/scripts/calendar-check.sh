#!/usr/bin/env bash
# Checks the Google Calendar settings WITHOUT printing any secret: can we sign in, can we see each branch
# calendar, can we write and remove an event. Run it on the machine that will run the server.
#
#   1) create the file  .env.calendar  in the repository root (it is git-ignored) with these lines:
#        APP_CALENDAR_CLIENT_ID=...
#        APP_CALENDAR_CLIENT_SECRET=...
#        APP_CALENDAR_REFRESH_TOKEN=...
#   2) bash backend/scripts/calendar-check.sh <calendarId> [<calendarId> ...]
#
# The values can also come from environment variables of the same names.
set -u
root="$(cd "$(dirname "$0")/../.." && pwd)"
if [ -f "$root/.env.calendar" ]; then
  set -a; . "$root/.env.calendar"; set +a
fi
: "${APP_CALENDAR_CLIENT_ID:?APP_CALENDAR_CLIENT_ID is not set (see the comment at the top of this script)}"
: "${APP_CALENDAR_CLIENT_SECRET:?APP_CALENDAR_CLIENT_SECRET is not set}"
: "${APP_CALENDAR_REFRESH_TOKEN:?APP_CALENDAR_REFRESH_TOKEN is not set}"
[ "$#" -ge 1 ] || { echo "usage: bash backend/scripts/calendar-check.sh <calendarId> [<calendarId> ...]"; exit 2; }

api="https://www.googleapis.com/calendar/v3"
fail=0
say() { printf '%s\n' "$*"; }
field() { sed -n "s/.*\"$1\" *: *\"\([^\"]*\)\".*/\1/p" | head -1; }

say "1) Signing in with the refresh token ..."
reply=$(curl -s -m 30 -w '\n%{http_code}' https://oauth2.googleapis.com/token \
  --data-urlencode "client_id=$APP_CALENDAR_CLIENT_ID" --data-urlencode "client_secret=$APP_CALENDAR_CLIENT_SECRET" \
  --data-urlencode "refresh_token=$APP_CALENDAR_REFRESH_TOKEN" --data-urlencode "grant_type=refresh_token")
code=$(printf '%s' "$reply" | tail -1); body=$(printf '%s' "$reply" | sed '$d')
if [ "$code" != "200" ]; then
  why=$(printf '%s' "$body" | field error)
  say "   FAILED (HTTP $code${why:+, $why})"
  case "$why" in
    invalid_client) say "   -> the Client ID or Client secret is wrong (check for extra spaces or a missing character)." ;;
    invalid_grant)  say "   -> the refresh token is wrong, expired or revoked. In Testing status it expires after 7 days; get a new one (and publish the app)." ;;
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
    say "   cannot open it (HTTP $code) -> wrong Calendar ID, or this Gmail account does not own/share it."; fail=1; continue
  fi
  say "   OK: $(printf '%s' "$info" | sed '$d' | field summary)"
  start=$(date -u -d '+1 day' +%Y-%m-%dT03:00:00Z 2>/dev/null || date -u -v+1d +%Y-%m-%dT03:00:00Z)
  end=$(date -u -d '+1 day' +%Y-%m-%dT03:30:00Z 2>/dev/null || date -u -v+1d +%Y-%m-%dT03:30:00Z)
  made=$(curl -s -m 30 -w '\n%{http_code}' -X POST -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
    -d "{\"summary\":\"[test] Clinic ERP calendar check\",\"start\":{\"dateTime\":\"$start\"},\"end\":{\"dateTime\":\"$end\"}}" \
    "$api/calendars/$(enc "$cal")/events")
  code=$(printf '%s' "$made" | tail -1); id=$(printf '%s' "$made" | sed '$d' | field id)
  if [ "$code" != "200" ] || [ -z "$id" ]; then
    say "   cannot write an event (HTTP $code) -> give this Gmail account 'Make changes to events' on the calendar."; fail=1; continue
  fi
  del=$(curl -s -m 30 -o /dev/null -w '%{http_code}' -X DELETE -H "Authorization: Bearer $token" "$api/calendars/$(enc "$cal")/events/$id")
  if [ "$del" = "204" ] || [ "$del" = "200" ]; then say "   OK: wrote and removed a test event."; else say "   wrote the test event but could not remove it (HTTP $del); delete '[test] Clinic ERP calendar check' by hand."; fail=1; fi
done

if [ "$fail" = "0" ]; then say "ALL GOOD: the settings work."; else say "Some checks failed (see above)."; fi
exit "$fail"
