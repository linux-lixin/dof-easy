#!/bin/sh
set -eu
[ "$#" -eq 1 ] || { echo 'invalid argument count' >&2; exit 2; }
for value in "$@"; do
    case "$value" in ''|*[!0-9]*) echo 'numeric argument required' >&2; exit 2;; esac
done
: "${DNF_DB_GAME_PASSWORD:?DNF_DB_GAME_PASSWORD is required}"
query="update taiwan_cain.charac_info set job=10 where charac_no=$1 and lev=1"
printf '%s\n' "$query" >> /dp2/script/covert_char_sql.log
MYSQL_PWD="$DNF_DB_GAME_PASSWORD" mysql -h127.0.0.1 -P3306 -ugame -e "$query"
