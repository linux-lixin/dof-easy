"""Check external DB privileges/schema before shared initialization writes SQL."""
import os
import subprocess
import sys

def check():
    if os.environ.get('DOF_DATABASE_MODE', 'existing') != 'existing' or os.environ.get('DOF_DATABASE_KIND', 'external') != 'external':
        raise ValueError('Original Qingkong requires external MySQL in existing mode')
    for prefix in ('CUR_MAIN_DB','CUR_SG_DB'):
        env=dict(os.environ,MYSQL_PWD=os.environ['DNF_DB_GAME_PASSWORD'])
        args=['/usr/local/bin/mysql','-h',os.environ[prefix+'_HOST'],'-P',os.environ[prefix+'_PORT'],'-ugame','-N','-e']
        schema='d_taiwan.accounts' if prefix=='CUR_MAIN_DB' else 'taiwan_'+os.environ['SERVER_GROUP_DB']+'_2nd.member_avatar_coin'
        result=subprocess.run(args+['SELECT 1 FROM '+schema+' LIMIT 1'],env=env,capture_output=True,timeout=5)
        if result.returncode:
            raise ValueError(prefix+' game account cannot read required schema; check instance initialization and scoped grants')

if __name__=='__main__':
    try: check()
    except (ValueError,subprocess.SubprocessError) as exc:
        # Do not print subprocess commands or SHOW GRANTS (contains password hashes).
        print(str(exc) if isinstance(exc,ValueError) else 'Database privilege check timed out',file=sys.stderr)
        sys.exit(1)
