"""Validate before touching persistent files or connecting to the database."""
import ipaddress
import os
from pathlib import Path
import re
import shlex
import subprocess
import sys

DEFAULTS = dict(SERVER_TYPE='ALL', SERVER_GROUP='3', SERVER_GROUP_DB='cain',
                CORE_PUBLIC_IP='127.0.0.1', MAIN_BRIDGE_IP='127.0.0.1',
                P2P_PUBLIC_IP='127.0.0.1', OPEN_CHANNEL='11,52', CLIENT_POOL_SIZE='3',
                GAME_CHANNEL_HOSTS='',
                AUTO_PUBLIC_IP='false', DDNS_ENABLE='false', DDNS_DOMAIN='', DDNS_INTERVAL='10',
                STARTUP_TIMEOUT='600', MYSQL_INIT_SSL_MODE='disabled', WEB_USER='admin')

def channels(value):
    result = set()
    for part in value.split(','):
        if not re.fullmatch(r'\d+(?:-\d+)?', part):
            raise ValueError('OPEN_CHANNEL must be channel numbers/ranges')
        bounds = [int(x) for x in part.split('-')]
        start, end = bounds[0], bounds[-1]
        if start > end or end > 56:
            raise ValueError('OPEN_CHANNEL contains an invalid range')
        result.update(range(start, end+1))
    if not result or not result <= ({1,6,7} | set(range(11,40)) | set(range(52,57))):
        raise ValueError('OPEN_CHANNEL contains unsupported channels')
    return sorted(result)

def channel_hosts(value, expected_channels):
    """Require one explicit IPv4 destination for every configured CORE channel."""
    result = {}
    for item in value.split(','):
        fields = item.strip().split('=')
        if len(fields) != 2 or not re.fullmatch(r'[0-9]+', fields[0]):
            raise ValueError('GAME_CHANNEL_HOSTS must be channel=IPv4 pairs')
        channel = int(fields[0])
        if channel in result:
            raise ValueError('GAME_CHANNEL_HOSTS contains duplicate channels')
        try:
            result[channel] = str(ipaddress.IPv4Address(fields[1]))
        except ValueError:
            raise ValueError('GAME_CHANNEL_HOSTS requires explicit IPv4 destinations')
    if set(result) != set(expected_channels):
        raise ValueError('GAME_CHANNEL_HOSTS must cover exactly OPEN_CHANNEL')
    return result


def validate(given):
    env = dict(DEFAULTS, **given)
    role = env['SERVER_TYPE']
    if role not in ('ALL','CORE','GAME','P2P'):
        raise ValueError('SERVER_TYPE must be ALL, CORE, GAME or P2P')
    for key in ('NB_SETUP_KEY','TS_AUTH_KEY'):
        if env.get(key):
            raise ValueError(key + ' is unsupported: Ubuntu uses direct networking')
    if role != 'P2P':
        for key in ('MAIN_MYSQL_HOST','MAIN_MYSQL_PORT','MAIN_MYSQL_ROOT_PASSWORD',
                    'MYSQL_HOST','MYSQL_PORT','DNF_DB_ROOT_PASSWORD','DNF_DB_GAME_PASSWORD'):
            if not env.get(key):
                raise ValueError(key + ' is required: database must be external')
        if not re.fullmatch(r'[A-Za-z0-9]{8}',env['DNF_DB_GAME_PASSWORD']):
            raise ValueError('DNF_DB_GAME_PASSWORD must be exactly 8 alphanumeric characters')
        for key in ('MAIN_MYSQL_ROOT_PASSWORD','DNF_DB_ROOT_PASSWORD'):
            if not re.fullmatch(r'[A-Za-z0-9]+',env[key]):
                raise ValueError(key + ' must be alphanumeric for legacy SQL scripts')
        for key in ('MAIN_MYSQL_PORT','MYSQL_PORT'):
            if not env[key].isdigit() or not 1 <= int(env[key]) <= 65535:
                raise ValueError(key + ' must be a TCP port')
        for key in ('MAIN_MYSQL_GAME_ALLOW_IP','MYSQL_GAME_ALLOW_IP'):
            if env.get(key) and not re.fullmatch(r'[0-9.%/]+', env[key]):
                raise ValueError(key + ' must be an IPv4 host/netmask/pattern')
    if not re.fullmatch('[1-6]', env['SERVER_GROUP']):
        raise ValueError('SERVER_GROUP must be 1..6')
    if not re.fullmatch('[A-Za-z0-9_]+', env['SERVER_GROUP_DB']):
        raise ValueError('SERVER_GROUP_DB contains invalid characters')
    if role in ('ALL','CORE','GAME'):
        env['OPEN_CHANNEL'] = ','.join(str(x) for x in channels(env['OPEN_CHANNEL']))
    if role in ('ALL','CORE'):
        selected = channels(env['OPEN_CHANNEL'])
        destinations = ({channel: '127.0.0.1' for channel in selected} if role == 'ALL'
                        else channel_hosts(env['GAME_CHANNEL_HOSTS'], selected))
        env['GAME_CHANNEL_HOSTS'] = ','.join(str(channel)+'='+destinations[channel] for channel in selected)
    if role in ('ALL','P2P') and not re.fullmatch(r'[1-9][0-9]{0,2}', env.get('P2P_RELAY_INDEX','')):
        raise ValueError('P2P_RELAY_INDEX must be a positive integer (1..999)')
    if role in ('ALL','CORE'):
        for key in ('WEB_PASS',):
            if not env.get(key): raise ValueError(key+' is required')
    for key in ('STARTUP_TIMEOUT','DDNS_INTERVAL','CLIENT_POOL_SIZE'):
        if not env[key].isdigit() or not 1 <= int(env[key]) <= 600:
            raise ValueError(key + ' must be 1..600')
    if env['MYSQL_INIT_SSL_MODE'] not in ('disabled','auto'):
        raise ValueError('MYSQL_INIT_SSL_MODE must be disabled or auto')
    for key in ('AUTO_PUBLIC_IP','DDNS_ENABLE'):
        if env[key] not in ('true','false'):
            raise ValueError(key + ' must be true or false')
    for key in ('WEB_USER','WEB_PASS'):
        if env.get(key) and not re.fullmatch('[A-Za-z0-9_]+',env[key]):
            raise ValueError(key + ' must be alphanumeric/underscore for legacy templates')
    if not env.get('PUBLIC_IP') and env['AUTO_PUBLIC_IP'] != 'true' and env['DDNS_ENABLE'] != 'true':
        raise ValueError('PUBLIC_IP or an enabled address discovery method is required')
    for key in ('PUBLIC_IP','CORE_PUBLIC_IP','MAIN_BRIDGE_IP','P2P_PUBLIC_IP'):
        if env.get(key):
            try: ipaddress.IPv4Address(env[key])
            except ValueError: raise ValueError(key + ' must be an explicit IPv4 address')
    if role != 'P2P':
        for prefix, source in (('CUR_MAIN_DB','MAIN_MYSQL'),('CUR_SG_DB','MYSQL')):
            env[prefix+'_HOST'] = env[source+'_HOST']
            env[prefix+'_PORT'] = env[source+'_PORT']
            env[prefix+'_GAME_ALLOW_IP'] = env.get(source+'_GAME_ALLOW_IP','')
        env['CUR_MAIN_DB_ROOT_PASSWORD'] = env['MAIN_MYSQL_ROOT_PASSWORD']
        env['CUR_SG_DB_ROOT_PASSWORD'] = env['DNF_DB_ROOT_PASSWORD']
    env['SERVER_GROUP_NAME'] = ('cain','diregie','siroco','prey','casillas','hilder')[int(env['SERVER_GROUP'])-1]
    return env

def main():
    try:
        env=validate(os.environ)
        if env['SERVER_TYPE'] != 'P2P':
            env['DEC_GAME_PWD'] = subprocess.check_output(['/TeaEncrypt',env['DNF_DB_GAME_PASSWORD']],text=True).strip()
        target=Path('/run/dnf'); target.mkdir(mode=0o700,exist_ok=True)
        output=target/'env.sh'
        # Write only adapter-owned configuration, never dump inherited credentials.
        keys=set(DEFAULTS) | {k for k in env if k.startswith(('CUR_','MAIN_MYSQL','MYSQL_','DNF_DB_'))} | {'DEC_GAME_PWD','SERVER_GROUP_NAME'}
        output.write_text(''.join('export '+k+'='+shlex.quote(env[k])+'\n' for k in sorted(keys) if k in env))
        output.chmod(0o600)
    except subprocess.CalledProcessError as exc:
        print('Ubuntu preflight: password encoder failed (exit '+str(exc.returncode)+')',file=sys.stderr); return 1
    except ValueError as exc:
        print('Ubuntu preflight: '+str(exc),file=sys.stderr); return 1
    return 0

if __name__ == '__main__':
    sys.exit(main())
