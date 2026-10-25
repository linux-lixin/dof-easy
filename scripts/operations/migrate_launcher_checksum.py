#!/usr/bin/env python3
"""Add nullable checksums to two own launcher tables; never load the destructive init.sql."""
import argparse
import importlib.util
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('manager', ROOT/'scripts/container/manage.py')
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)
TABLES = ('CLIENT_LAUNCHER_CONFIG', 'CLIENT_LAUNCHER_VERSION')

def migrate(instance):
    def sql(query):
        return m.compose(instance, 'exec', '-T', 'db', 'sh', '-c',
            'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -N',
            input=query, text=True, capture_output=True).stdout.strip()
    missing = []
    for table in TABLES:
        if sql("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='dnf_service' AND TABLE_NAME='"+table+"';") != '1':
            raise ValueError('Missing own launcher table: '+table)
        column=sql("SELECT COLUMN_TYPE,IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='dnf_service' AND TABLE_NAME='"+table+"' AND COLUMN_NAME='SHA256';")
        if column and column != 'varchar(64)\tYES':
            raise ValueError('Incompatible checksum column: '+table)
        if not column: missing.append(table)
    if not missing: return {'added': [], 'alreadyCompatible': True}
    output=m.STATE/'backups'/('launcher-checksums-'+instance.name+'-'+datetime.now().strftime('%Y%m%d-%H%M%S')+'.sql')
    output.parent.mkdir(parents=True,exist_ok=True)
    output.parent.chmod(0o700)
    with output.open('xb') as stream:
        output.chmod(0o600)
        m.compose(instance, 'exec', '-T', 'db', 'sh', '-c',
            'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --lock-tables dnf_service CLIENT_LAUNCHER_CONFIG CLIENT_LAUNCHER_VERSION', stdout=stream)
    if not output.stat().st_size: raise RuntimeError('Empty launcher backup')
    for table in missing:
        # DDL is not cross-table atomic. A partial migration is safe to retry;
        # old application versions ignore these nullable columns.
        sql('ALTER TABLE dnf_service.`'+table+'` ADD COLUMN SHA256 VARCHAR(64) NULL;')
    for table in TABLES:
        assert sql("SELECT COLUMN_TYPE,IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='dnf_service' AND TABLE_NAME='"+table+"' AND COLUMN_NAME='SHA256';")=='varchar(64)\tYES'
    return {'added':missing,'backup':output.name}

if __name__=='__main__':
    import json
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--instance',required=True)
    args=parser.parse_args()
    instance=m.instance_path(args.instance)
    with m.locked(instance):
        # Call while the instance's admin service is stopped.
        running=m.compose(instance,'ps','--status','running','--services',text=True,capture_output=True).stdout.split()
        if 'admin' in running: raise RuntimeError('Stop this instance admin before migrating')
        print(json.dumps(migrate(instance)))
