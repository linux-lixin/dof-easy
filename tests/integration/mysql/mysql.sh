#!/bin/sh
set -eu
export LD_LIBRARY_PATH=/tmp/mysql/lib/x86_64-linux-gnu
export PATH=/tmp:/tmp/mysql/usr/bin:/usr/bin:/bin
cp /tmp/mysql/usr/bin/mysqldump /tmp/dof-mysqldump56
chmod 700 /tmp/dof-mysqldump56
ip link set lo up
mkdir -p /tmp/output/mysql/mysql /tmp/output/run /tmp/output/files
(printf 'USE mysql;\n'; cat /tmp/mysql/usr/share/mysql/mysql_system_tables.sql /tmp/mysql/usr/share/mysql/mysql_system_tables_data.sql) | /tmp/mysql/usr/sbin/mysqld --no-defaults --bootstrap --user=root --basedir=/tmp/mysql/usr --datadir=/tmp/output/mysql --default-storage-engine=MyISAM > /tmp/output/bootstrap.log 2>&1
/tmp/mysql/usr/sbin/mysqld --no-defaults --user=root --basedir=/tmp/mysql/usr --datadir=/tmp/output/mysql --secure-file-priv=/tmp/output/files --socket=/tmp/output/run/mysql.sock --pid-file=/tmp/output/run/mysql.pid --bind-address=127.0.0.1 --port=3307 --skip-name-resolve --sql-mode=NO_ENGINE_SUBSTITUTION --innodb-buffer-pool-size=64M --max-allowed-packet=64M --log-error=/tmp/output/mysql.log &
pid=$!
trap 'kill "$pid" 2>/dev/null || true; wait "$pid" 2>/dev/null || true' EXIT
for attempt in $(seq 1 40); do
 if mysql --socket=/tmp/output/run/mysql.sock -uroot -N -e 'SELECT VERSION()' > /tmp/output/version.txt 2>/dev/null; then break; fi
 sleep 1
done
test -s /tmp/output/version.txt
cp /tmp/TeaEncrypt /tmp/TeaEncrypt-run
chmod 700 /tmp/TeaEncrypt-run
python3 /tmp/check.py > /tmp/output/result.log 2>&1
