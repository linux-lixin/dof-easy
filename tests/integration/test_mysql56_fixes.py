"""Disposable local MySQL 5.6 checks. No published ports or remote DB access."""
import os
from pathlib import Path
import re
import subprocess
import tarfile
import tempfile
import time
import uuid

ROOT=Path(__file__).resolve().parents[2]
INIT=ROOT/'services/game/assets/home/template/init'
IMAGE='mysql:5.6@sha256:20575ecebe6216036d25dab5903808211f1e9ba63dc7825ac20cb975e34cfcae'
NAME='dnf-fix-test-'+uuid.uuid4().hex[:10]

def docker(*args, data=None, check=True):
    r=subprocess.run(['docker',*args],input=data,text=True,encoding='utf8',capture_output=True)
    if check and r.returncode:
        raise AssertionError(r.stderr)
    return r

def sql(text,check=True):
    return docker('exec','-i',NAME,'mysql','-uroot','--default-character-set=utf8','-N',data=text,check=check)

def ddl(source,table):
    return re.search(r'CREATE TABLE `'+re.escape(table)+r'`\s+\(.*?\n\).*?;',source,re.S).group()

def copy(path,target): docker('cp',str(path),NAME+':'+target)

try:
    docker('run','-d','--network','none','--name',NAME,'-e','MYSQL_ALLOW_EMPTY_PASSWORD=yes',IMAGE)
    for _ in range(90):
        if docker('exec',NAME,'mysqladmin','-h127.0.0.1','-uroot','ping',check=False).returncode==0: break
        time.sleep(1)
    else: raise AssertionError('MySQL did not become ready')
    print('MySQL ready:',sql('SELECT VERSION();').stdout.strip(),flush=True)
    with tarfile.open(INIT/'init_sql.tgz') as archive:
        login=archive.extractfile('taiwan_login.sql').read().decode('utf8')
        main=archive.extractfile('d_taiwan.sql').read().decode('utf8')
        log=archive.extractfile('taiwan_cain_log.sql').read().decode('utf8')
    seed=ddl(login,'log_query_ref')+'\n'+'\n'.join(x for x in login.splitlines() if x.startswith('INSERT INTO `log_query_ref` VALUES'))
    sql('CREATE DATABASE taiwan_login; CREATE DATABASE d_taiwan; CREATE DATABASE taiwan_cain_log; USE taiwan_login;\n'+seed)
    bad=sql("INSERT INTO taiwan_login.log_query_ref(query_hash,query) VALUES('testinsert','SELECT 1');",False)
    assert bad.returncode!=0 and '1467' in bad.stderr,bad.stderr
    print('Reproduced stock seed error 1467.',flush=True)
    for schema,table in [('d_taiwan','log_query_stat'),('taiwan_cain_log','log_query_stat'),('taiwan_cain_log','log_query_stat_del')]:
        sql('USE '+schema+';\n'+ddl(main if schema=='d_taiwan' else log,table))
    copy(INIT/'repair_existing_log_ref.sh','/tmp/repair.sh')
    repair=('exec','-e','GAME_CHANNELS_STOPPED=yes','-e','SERVER_GROUP_DB=cain',NAME,'bash','/tmp/repair.sh','-uroot')
    # Referenced history must block a remap and preserve the original record.
    sql('INSERT INTO taiwan_cain_log.log_query_stat (occ_time,q_id,gc_no) VALUES (NOW(),65535,1);')
    assert docker(*repair,check=False).returncode!=0
    assert sql('SELECT MAX(q_id) FROM taiwan_login.log_query_ref;').stdout.strip()=='65535'
    sql('DELETE FROM taiwan_cain_log.log_query_stat;')  # isolated fixture only
    docker(*repair)
    assert sql('SELECT COUNT(*),MAX(q_id) FROM taiwan_login.log_query_ref;').stdout.strip()=='394\t50939'
    sql("INSERT INTO taiwan_login.log_query_ref(query_hash,query) VALUES('testinsert','SELECT 1');")
    assert sql('SELECT MAX(q_id) FROM taiwan_login.log_query_ref;').stdout.strip()=='50940'
    docker(*repair)  # repeat is a no-op
    print('Existing DB repair: references protected, rows preserved, new inserts work, repeat safe.',flush=True)

    with tempfile.TemporaryDirectory() as td:
        td=Path(td); (td/'taiwan_login.sql').write_text(login,encoding='utf8')
        docker('exec',NAME,'mkdir','-p','/tmp/seed')
        copy(td/'taiwan_login.sql','/tmp/seed/taiwan_login.sql')
        copy(INIT/'prepare_login_seed.sh','/tmp/prepare.sh')
        docker('exec',NAME,'bash','/tmp/prepare.sh','/tmp/seed')
        fixed=docker('exec',NAME,'cat','/tmp/seed/taiwan_login.sql').stdout
        fixed_seed=ddl(fixed,'log_query_ref')+'\n'+'\n'.join(x for x in fixed.splitlines() if x.startswith('INSERT INTO `log_query_ref` VALUES'))
        sql('USE taiwan_login; DROP TABLE log_query_ref;\n'+fixed_seed)
        sql("INSERT INTO taiwan_login.log_query_ref(query_hash,query) VALUES('freshinsert','SELECT 1');")
        assert sql('SELECT COUNT(*),MAX(q_id) FROM taiwan_login.log_query_ref;').stdout.strip()=='395\t50940'
        print('Fresh patched seed imports and allocates ID 50940.',flush=True)

        # December -> January rollover, with both tables created twice.
        sql('USE taiwan_cain_log;\n'+ddl(log,'log_response_time_201604'))
        (td/'date').write_bytes(b'#!/bin/sh\ncase "$*" in\n +%Y%m) echo 202612;;\n +%Y-%m-01) echo 2026-12-01;;\n *) echo 202701;;\nesac\n')
        docker('exec',NAME,'mkdir','-p','/tmp/mock-bin')
        copy(td/'date','/tmp/mock-bin/date')
        docker('exec',NAME,'chmod','+x','/tmp/mock-bin/date')
        copy(INIT/'daily_job/response_time_tables.sh','/tmp/monthly.sh')
        sql("GRANT ALL ON taiwan_cain_log.* TO 'game'@'127.0.0.1' IDENTIFIED BY 'testonly';")
        for _ in range(2):
            docker('exec','-e','CUR_SG_DB_HOST=127.0.0.1','-e','CUR_SG_DB_PORT=3306',
                   '-e','SERVER_GROUP_DB=cain','-e','DNF_DB_GAME_PASSWORD=testonly',NAME,
                   'bash','-c','PATH=/tmp/mock-bin:$PATH bash /tmp/monthly.sh')
        assert sql("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='taiwan_cain_log' AND TABLE_NAME IN ('log_response_time_202612','log_response_time_202701');").stdout.strip()=='2'
        print('Monthly maintenance: year rollover and repeat execution passed.',flush=True)

    sql('USE d_taiwan;\n'+ddl(main,'accounts'))
    backend=(ROOT/'services/admin/database/init.sql').read_text(encoding='utf8').replace('${DB_NAME}','dnf_service')
    sql(backend)
    bootstrap=(ROOT/'deploy/shared/bootstrap-admin.sql.example').read_text(encoding='utf8')
    bootstrap=bootstrap.replace('CHANGE_ME_NEW_PRIVATE_ADMIN_PASSWORD','localtestonly').replace('CHANGE_ME_DIFFERENT_PRIVATE_TEMP_SECRET','testsecretone')
    sql(bootstrap)
    assert sql("SELECT COUNT(*) FROM d_taiwan.accounts WHERE accountname='admin' AND admin=1;").stdout.strip()=='1'
    assert sql('SELECT COUNT(*) FROM dnf_service.ADMIN_TEMP_PASSWORD WHERE id=1;').stdout.strip()=='1'
    upsert=bootstrap[bootstrap.index('INSERT INTO dnf_service.ADMIN_TEMP_PASSWORD'):]
    sql(upsert.replace('testsecretone','testsecrettwo'))
    assert sql("SELECT COUNT(*) FROM dnf_service.ADMIN_TEMP_PASSWORD WHERE id=1 AND PASSWORD='testsecrettwo';").stdout.strip()=='1'
    print('Backend original schema + bootstrap: admin created; empty/existing auth row passed.',flush=True)
    print('All isolated MySQL 5.6 checks passed.',flush=True)
finally:
    docker('rm','-fv',NAME,check=False)
