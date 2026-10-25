#!/bin/bash
set -e
case "${DOF_DATABASE_MODE:-provision}" in
  existing)
    echo "Existing game database: legacy initialization and grant rewriting skipped."
    exit 0
    ;;
  provision) ;;
  *) echo "DOF_DATABASE_MODE must be existing or provision" >&2; exit 1 ;;
esac
if [ ! -d /home/template/init/init_sql ]; then
  mkdir -p /home/template/init/init_sql
  tar -zxf /home/template/init/init_sql.tgz -C /home/template/init/init_sql
fi
bash /home/template/init/prepare_login_seed.sh
bash /home/template/init/init_main_db.sh
bash /home/template/init/init_server_group_db.sh
