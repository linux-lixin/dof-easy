#!/bin/bash
set -euo pipefail
case "${SERVER_GROUP_DB:-}" in
    ''|*[!a-zA-Z0-9_]*) echo "Invalid SERVER_GROUP_DB" >&2; exit 1 ;;
esac
current_month=$(date +%Y%m)
month_start=$(date +%Y-%m-01)
next_month=$(date -d "$month_start +1 month" +%Y%m)
for month in "$current_month" "$next_month"; do
    case "$month" in
        [0-9][0-9][0-9][0-9][0-9][0-9]) ;;
        *) echo "Invalid response-log month: $month" >&2; exit 1 ;;
    esac
    mysql -h "$CUR_SG_DB_HOST" -P "$CUR_SG_DB_PORT" -u game -p"$DNF_DB_GAME_PASSWORD" <<SQL
CREATE TABLE IF NOT EXISTS taiwan_${SERVER_GROUP_DB}_log.log_response_time_${month}
LIKE taiwan_${SERVER_GROUP_DB}_log.log_response_time_201604;
SQL
done
