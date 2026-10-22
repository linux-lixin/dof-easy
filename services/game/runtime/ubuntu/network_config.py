"""Prepare Ubuntu CORE routing without confusing advertised IPs with local peers.

Only image templates are changed. Persistent user files and wire protocols are
untouched; the shared starters copy these templates into their runtime locations.
"""
import os
from pathlib import Path
import sys

from config import channel_hosts, channels


def configure(root, env):
    role = env.get('SERVER_TYPE', 'ALL')
    if role not in ('ALL', 'CORE'):
        return
    selected = channels(env.get('OPEN_CHANNEL', '11,52'))
    hosts = ({channel: '127.0.0.1' for channel in selected} if role == 'ALL'
             else channel_hosts(env.get('GAME_CHANNEL_HOSTS', ''), selected))
    group = env.get('SERVER_GROUP', '3')
    if group not in ('1', '2', '3', '4', '5', '6'):
        raise ValueError('SERVER_GROUP must be 1..6')
    root = Path(root)
    updates = {}
    for service in ('manager', 'dbmw_mnt', 'dbmw_guild', 'dbmw_stat'):
        path = root/service/'table/server_config.tbl'
        updates[path] = path.read_text(encoding='utf8').replace('PUBLIC_IP', '127.0.0.1').replace('SERVER_GROUP', group)
    game_rows = ['1 {} {} {} {}10{:02d}'.format(group, channel, hosts[channel], group, channel)
                 for channel in selected]
    for service in ('statics', 'coserver'):
        path = root/service/'table/server_config.tbl'
        lines = []
        inserted = False
        for line in path.read_text(encoding='utf8').splitlines():
            fields = line.split()
            if fields and fields[0] == '1':
                if not inserted:
                    lines.extend(game_rows)
                    inserted = True
                continue
            lines.append(line.replace('SERVER_GROUP', group))
        if not inserted:
            lines.extend(game_rows)
        updates[path] = '\n'.join(lines)+'\n'
    # Validate/read every input before changing any template.
    for path, text in updates.items():
        path.write_text(text, encoding='utf8')


if __name__ == '__main__':
    try:
        configure('/home/template/neople', os.environ)
    except (ValueError, OSError) as exc:
        print('Ubuntu network configuration: '+str(exc), file=sys.stderr)
        sys.exit(1)
