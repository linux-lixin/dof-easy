#!/bin/sh
set -eu
export LD_LIBRARY_PATH=/inputs/mysql/lib/x86_64-linux-gnu
mkdir -p /tmp/mysql/mysql /tmp/mysql-run /tmp/mysql-files
(printf 'USE mysql;\n'; cat /inputs/mysql/usr/share/mysql/mysql_system_tables.sql /inputs/mysql/usr/share/mysql/mysql_system_tables_data.sql) | /inputs/mysql/usr/sbin/mysqld --no-defaults --bootstrap --user=root --basedir=/inputs/mysql/usr --datadir=/tmp/mysql --default-storage-engine=MyISAM > /output/bootstrap.log 2>&1
/inputs/mysql/usr/sbin/mysqld --no-defaults --user=root --basedir=/inputs/mysql/usr --datadir=/tmp/mysql --socket=/tmp/mysql-run/mysql.sock --pid-file=/tmp/mysql-run/mysql.pid --skip-networking --secure-file-priv=/tmp/mysql-files --innodb-buffer-pool-size=32M --general-log=0 --log-error=/output/mysql.log &
dbpid=$!
trap 'kill "$dbpid" 2>/dev/null || true; wait "$dbpid" 2>/dev/null || true' EXIT
for attempt in $(seq 1 30); do
 if /inputs/mysql/usr/bin/mysql --no-defaults --socket=/tmp/mysql-run/mysql.sock -uroot -N -e 'SELECT VERSION()' > /output/db-version.txt 2>/dev/null; then break; fi
 sleep 1
done
test -s /output/db-version.txt
python3 /inputs/probe.py
