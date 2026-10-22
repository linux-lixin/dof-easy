"""Check external DB privileges/schema before shared initialization writes SQL."""
import os
import subprocess
import sys

def check():
    provisioning=os.environ['SERVER_TYPE'] in ('ALL','CORE')
    for prefix in ('CUR_MAIN_DB','CUR_SG_DB'):
        env=dict(os.environ,MYSQL_PWD=os.environ[prefix+'_ROOT_PASSWORD'] if provisioning else os.environ['DNF_DB_GAME_PASSWORD'])
        args=['/usr/local/bin/mysql','-h',os.environ[prefix+'_HOST'],'-P',os.environ[prefix+'_PORT'],'-uroot' if provisioning else '-ugame','-N','-e']
        if provisioning:
            result=subprocess.run(args+['SHOW GRANTS FOR CURRENT_USER()'],env=env,capture_output=True,text=True,timeout=5)
            # Initialization itself grants ALL to game and updates mysql.user.
            if result.returncode or not any('GRANT ALL PRIVILEGES ON *.*' in row and 'WITH GRANT OPTION' in row for row in result.stdout.splitlines()):
                raise ValueError(prefix+' needs global ALL PRIVILEGES WITH GRANT OPTION for initial provisioning')
        else:
            schema='d_taiwan.accounts' if prefix=='CUR_MAIN_DB' else 'taiwan_'+os.environ['SERVER_GROUP_DB']+'_2nd.member_avatar_coin'
            result=subprocess.run(args+['SELECT 1 FROM '+schema+' LIMIT 1'],env=env,capture_output=True,timeout=5)
            if result.returncode: raise ValueError(prefix+' game account cannot read required schema from this node; check CORE initialization and game@host grants')

if __name__=='__main__':
    try: check()
    except (ValueError,subprocess.SubprocessError) as exc:
        # Do not print subprocess commands or SHOW GRANTS (contains password hashes).
        print(str(exc) if isinstance(exc,ValueError) else 'Database privilege check timed out',file=sys.stderr)
        sys.exit(1)
