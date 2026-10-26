#!/bin/sh
set -eu
[ "$#" -eq 3 ] || { echo 'invalid argument count' >&2; exit 2; }
for value in "$@"; do
    case "$value" in ''|*[!0-9]*) echo 'numeric argument required' >&2; exit 2;; esac
done
: "${DNF_DB_GAME_PASSWORD:?DNF_DB_GAME_PASSWORD is required}"
query="delete from taiwan_cain_2nd.creature_items where charac_no=$1 and slot=$2 and it_id=$3"
printf '%s\n' "$query" >> /dp2/script/delete_creature_item_sql.log
MYSQL_PWD="$DNF_DB_GAME_PASSWORD" mysql -h127.0.0.1 -P3306 -ugame -e "$query"
