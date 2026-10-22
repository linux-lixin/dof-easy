#!/bin/bash
# Repair only the known bundled seed, BEFORE first import. Never edits live rows.
set -euo pipefail
seed="${1:-/home/template/init/init_sql}/taiwan_login.sql"
old="INSERT INTO \`log_query_ref\` VALUES ('6d986dad283ff5a4', 65535,"
fixed="INSERT INTO \`log_query_ref\` VALUES ('6d986dad283ff5a4', 50939,"
if grep -Fq "$fixed" "$seed"; then
    echo "log_query_ref seed already repaired."
    exit 0
fi
if ! grep -Fq "$old" "$seed"; then
    echo "Unrecognized log_query_ref seed; inspect before importing." >&2
    exit 1
fi
# Check the replacement is unused, and the expected stock row layout is intact.
if ! awk '/^INSERT INTO `log_query_ref` VALUES/ {
    split($0, fields, ", "); id=fields[2]+0; count++;
    if (id==50939 || (id>50938 && id!=65535)) bad=1
} END {exit !(count==394 && !bad)}' "$seed"; then
    echo "Modified seed detected; refusing to remap query IDs automatically." >&2
    exit 1
fi
sed -i \
    -e "/^CREATE TABLE \`log_query_ref\`/,/^).*;$/s/AUTO_INCREMENT=65536/AUTO_INCREMENT=50940/" \
    -e "s/^INSERT INTO \`log_query_ref\` VALUES ('6d986dad283ff5a4', 65535,/INSERT INTO \`log_query_ref\` VALUES ('6d986dad283ff5a4', 50939,/" "$seed"
echo "Repaired bundled log_query_ref seed (394 rows preserved)."
