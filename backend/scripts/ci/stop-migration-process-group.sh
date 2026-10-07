#!/usr/bin/env bash
set -euo pipefail

pid_file=${1:?usage: stop-migration-process-group.sh PID_FILE [PORT]}
port=${2:-8080}

group_has_process() {
  ps -eo pgid= | awk -v expected="$1" '$1 == expected { found = 1 } END { exit !found }'
}

group_has_session_leader() {
  ps -eo pgid=,sid= | awk -v expected="$1" '$1 == expected && $2 == expected { found = 1 } END { exit !found }'
}

port_has_listener() {
  ss -H -ltn "sport = :$port" | grep -q .
}

if [[ ! -s "$pid_file" ]]; then
  echo 'No migration process group was recorded.'
  if port_has_listener; then
    echo "Port $port still has a listener although no owned process group was recorded." >&2
    ss -ltnp "sport = :$port" >&2
    exit 1
  fi
  echo "Port $port has no listener."
  exit 0
fi

pgid=$(cat "$pid_file")
if [[ ! "$pgid" =~ ^[0-9]+$ ]] || (( pgid < 2 )); then
  echo "Invalid migration process-group ID in $pid_file." >&2
  exit 1
fi
if ! group_has_process "$pgid"; then
  echo "No process remains in recorded migration process group $pgid."
  if port_has_listener; then
    echo "Port $port still has a listener although the recorded process group has exited." >&2
    ss -ltnp "sport = :$port" >&2
    exit 1
  fi
  rm -f "$pid_file"
  echo "Port $port has no listener; removed the stale process-group marker."
  exit 0
fi

own_pgid=$(ps -o pgid= -p "$$" | tr -d '[:space:]')
if [[ "$pgid" == "$own_pgid" ]] || ! group_has_session_leader "$pgid"; then
  echo "Refusing to signal unverified process group $pgid." >&2
  exit 1
fi

echo "Processes in owned migration session before stop (PID PPID PGID SID COMMAND):"
ps -eo pid=,ppid=,pgid=,sid=,comm= | awk -v expected="$pgid" '$3 == expected'

if group_has_process "$pgid"; then
  if ! kill -TERM -- "-$pgid" && group_has_process "$pgid"; then
    echo "Could not send SIGTERM to owned migration process group $pgid." >&2
    exit 1
  fi
fi

for attempt in $(seq 1 30); do
  if ! group_has_process "$pgid"; then
    break
  fi
  sleep 1
done

if group_has_process "$pgid"; then
  echo "Owned migration process group $pgid did not stop gracefully; escalating SIGKILL." >&2
  if ! kill -KILL -- "-$pgid" && group_has_process "$pgid"; then
    echo "Could not send SIGKILL to owned migration process group $pgid." >&2
    exit 1
  fi
  for attempt in $(seq 1 5); do
    if ! group_has_process "$pgid"; then
      break
    fi
    sleep 1
  done
fi

if group_has_process "$pgid"; then
  echo "Processes remain in owned migration process group $pgid after bounded cleanup." >&2
  ps -eo pid=,ppid=,pgid=,sid=,comm= | awk -v expected="$pgid" '$3 == expected' >&2
  exit 1
fi
if port_has_listener; then
  echo "Owned migration process group $pgid stopped but port $port still has a listener." >&2
  ss -ltnp "sport = :$port" >&2
  exit 1
fi

rm -f "$pid_file"
echo "No process remains in migration process group $pgid; port $port is free."
