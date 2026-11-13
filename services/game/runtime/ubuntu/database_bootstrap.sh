#!/bin/bash
set -e
if [ "${DOF_DATABASE_MODE:-existing}" != existing ] || [ "${DOF_DATABASE_KIND:-external}" != external ]; then
  echo 'Original Qingkong requires external MySQL in existing mode' >&2
  exit 1
fi
echo 'Existing game database: legacy initialization and grant rewriting skipped.'
python3 /opt/dnf/ubuntu/external_database.py configure
