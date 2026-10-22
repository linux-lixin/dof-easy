#!/bin/bash
set -e
umask 077
python3 /opt/dnf/ubuntu/config.py
source /run/dnf/env.sh
python3 /opt/dnf/ubuntu/pvf.py
python3 /opt/dnf/ubuntu/managed.py
source /opt/dnf/ubuntu/wait.sh
if [ "$SERVER_TYPE" != P2P ]; then
  wait_tcp "$CUR_MAIN_DB_HOST" "$CUR_MAIN_DB_PORT" main-database
  wait_tcp "$CUR_SG_DB_HOST" "$CUR_SG_DB_PORT" group-database
  # Authentication failures must stop before shared initialization can write SQL.
  MYSQL_PWD="$CUR_MAIN_DB_ROOT_PASSWORD" mysql -h "$CUR_MAIN_DB_HOST" -P "$CUR_MAIN_DB_PORT" -uroot -e 'SELECT 1' >/dev/null
  MYSQL_PWD="$CUR_SG_DB_ROOT_PASSWORD" mysql -h "$CUR_SG_DB_HOST" -P "$CUR_SG_DB_PORT" -uroot -e 'SELECT 1' >/dev/null
  python3 /opt/dnf/ubuntu/database_preflight.py
fi
python3 /opt/dnf/ubuntu/network_config.py
exec bash /opt/dnf/ubuntu/bootstrap.sh
