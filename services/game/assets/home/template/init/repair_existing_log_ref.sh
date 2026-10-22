#!/bin/bash
# Offline repair for the known original seed on a single shared MySQL instance.
# Stop ALL game channels using taiwan_login before running; pass mysql options.
set -euo pipefail
: "${GAME_CHANNELS_STOPPED:?Set GAME_CHANNELS_STOPPED=yes after stopping all game channels}"
[ "$GAME_CHANNELS_STOPPED" = yes ] || exit 1
group=${SERVER_GROUP_DB:-cain}
case "$group" in ''|*[!a-zA-Z0-9_]*) echo 'Invalid server group' >&2; exit 1;; esac
query() { mysql "$@"; }
maximum=$(query "$@" -N -e 'SELECT MAX(q_id) FROM taiwan_login.log_query_ref;')
if [ "$maximum" -lt 65535 ]; then
    echo 'No saturated reference ID; no changes made.'
    exit 0
fi
stock=$(query "$@" -N -e "SELECT COUNT(*) FROM taiwan_login.log_query_ref
 WHERE q_id=65535 AND query_hash='6d986dad283ff5a4';")
rows=$(query "$@" -N -e 'SELECT COUNT(*) FROM taiwan_login.log_query_ref;')
[ "$stock" = 1 ] && [ "$rows" = 394 ] || { echo 'Not the known seed; manual migration required.' >&2; exit 1; }
for table in d_taiwan.log_query_stat taiwan_${group}_log.log_query_stat taiwan_${group}_log.log_query_stat_del; do
    used=$(query "$@" -N -e "SELECT COUNT(*) FROM $table WHERE q_id IN (50939,65535);")
    [ "$used" = 0 ] || { echo "Referenced IDs in $table; manual migration required." >&2; exit 1; }
done
used=$(query "$@" -N -e 'SELECT COUNT(*) FROM taiwan_login.log_query_ref WHERE q_id BETWEEN 50939 AND 65534;')
[ "$used" = 0 ] || { echo 'Replacement range is occupied; stopping.' >&2; exit 1; }
query "$@" <<SQL
LOCK TABLES taiwan_login.log_query_ref WRITE,
 d_taiwan.log_query_stat WRITE, taiwan_${group}_log.log_query_stat WRITE,
 taiwan_${group}_log.log_query_stat_del WRITE;
UPDATE taiwan_login.log_query_ref SET q_id=50939
 WHERE q_id=65535 AND query_hash='6d986dad283ff5a4';
UNLOCK TABLES;
ALTER TABLE taiwan_login.log_query_ref AUTO_INCREMENT=50940;
SELECT COUNT(*) AS preserved_rows,MAX(q_id) AS max_id FROM taiwan_login.log_query_ref;
SQL
