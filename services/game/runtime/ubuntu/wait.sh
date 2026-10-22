#!/bin/bash
wait_tcp() {
  local host="$1" port="$2" name="$3" deadline=$((SECONDS + ${STARTUP_TIMEOUT:-600}))
  echo "Waiting for $name ($host:$port), limit ${STARTUP_TIMEOUT:-600}s"
  until timeout 2 nc -z -w 2 "$host" "$port" >/dev/null 2>&1; do
    if (( SECONDS >= deadline )); then
      echo "Dependency timeout: $name ($host:$port)" >&2; return 1
    fi
    sleep 1
  done
}
wait_file() {
  local file="$1" deadline=$((SECONDS + ${STARTUP_TIMEOUT:-600}))
  until [ -s "$file" ]; do
    if (( SECONDS >= deadline )); then
      echo "Dependency timeout: $file" >&2; return 1
    fi
    sleep 1
  done
}
