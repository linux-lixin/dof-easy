#!/usr/bin/env python3
"""Build a deterministic, offline vipai 86 overlay without running any asset.

Original downloads remain untouched. Only the ordinary DP2/Frida archives and
the 86 executable are selected. Existing output is accepted only if identical;
choose a new --output to prepare a changed version for review.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import sys
import tarfile
import tempfile

ROOT = Path(__file__).resolve().parents[2]
GAME = 'df_game_r_files__unnotice86.tar.gz'
DP2 = 'dp2frida__dp2.tar.gz'
FRIDA = 'dp2frida__frida.tar.gz'
SQL = 'mysql_files__dnfsql_5.6.51_20230919.sql'
SERVER = 'server_files__Server20230919.tar.gz'
FRIDA_FILES = {'frida.so', 'frida.js', 'frida.config', 'frida_config.json'}
SERVER_FILES = {
    'home/neople/' + path for path in (
        'statics/df_statics_r', 'manager/df_manager_r', 'dbmw_guild/df_dbmw_r',
        'stun/df_stun_r', 'dbmw_mnt/df_dbmw_r', 'coserver/df_coserver_r',
        'auction/df_auction_r', 'relay/df_relay_r', 'bridge/df_bridge_r',
        'monitor/df_monitor_r', 'community/df_community_r', 'dbmw_stat/df_dbmw_r',
        'guild/df_guild_r', 'point/df_point_r', 'channel/df_channel_r')
}
REPORT_STUB = b'''-- Outbound reporting disabled in the vipai86 deployment overlay.
local M = {}
function M.create(host, port)
    return { send = function(...) end, close = function(...) end }
end
return M
'''
MAX_TOTAL_BYTES = 512 * 1024 * 1024
MAX_MEMBERS = 10000


def sha256(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def safe_name(name):
    """Validate before selection, including names of files we will not copy."""
    if not name or name.startswith('/') or '\\' in name or ':' in name:
        raise ValueError('unsafe archive path: ' + repr(name))
    parts = name.rstrip('/').split('/')
    while parts and parts[0] == '.':
        parts.pop(0)
    if not parts:
        raise ValueError('empty archive path')
    for part in parts:
        if (part in ('', '.', '..') or part.endswith((' ', '.'))
                or any(ord(c) < 32 for c in part)
                or re.match(r'^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\.|$)', part, re.I)):
            raise ValueError('unsafe archive path: ' + repr(name))
    return '/'.join(parts)


def verified_sources(source, include_server, plugins_only=False):
    manifest = json.loads((source / 'manifest.json').read_text(encoding='utf-8-sig'))
    if not isinstance(manifest, list):
        raise ValueError('source manifest must be a list')
    rows = {}
    for row in manifest:
        name = row['id']
        if name in rows:
            raise ValueError('duplicate manifest id: ' + name)
        rows[name] = row
    selected = ([] if plugins_only else [GAME]) + [DP2, FRIDA, SQL] + ([SERVER] if include_server else [])
    verified = []
    for name in selected:
        row = rows.get(name)
        path = source / name
        if not row or row.get('status') != 'downloaded':
            raise ValueError('missing downloaded source: ' + name)
        if path.is_symlink() or not path.is_file():
            raise ValueError('source must be a regular file: ' + name)
        if (path.stat().st_size != row.get('size') or sha256(path) != row.get('sha256')):
            raise ValueError('source hash/size mismatch: ' + name)
        verified.append({'id': name, 'size': row['size'], 'sha256': row['sha256']})
    return verified


def shell_helper(name):
    """Keep original item operations, validate numbers, and use runtime secrets."""
    count = 3 if name == 'delete_creature_item.sh' else 1
    query = ('delete from taiwan_cain_2nd.creature_items where charac_no=$1 and slot=$2 and it_id=$3'
             if count == 3 else 'update taiwan_cain.charac_info set job=10 where charac_no=$1 and lev=1')
    log_name = 'delete_creature_item_sql.log' if count == 3 else 'covert_char_sql.log'
    return ('''#!/bin/sh
set -eu
[ "$#" -eq %d ] || { echo 'invalid argument count' >&2; exit 2; }
for value in "$@"; do
    case "$value" in ''|*[!0-9]*) echo 'numeric argument required' >&2; exit 2;; esac
done
: "${DNF_DB_GAME_PASSWORD:?DNF_DB_GAME_PASSWORD is required}"
query="%s"
printf '%%s\\n' "$query" >> /dp2/script/%s
MYSQL_PWD="$DNF_DB_GAME_PASSWORD" mysql -h127.0.0.1 -P3306 -ugame -e "$query"
''' % (count, query, log_name)).encode('utf-8')


def destination(archive, name):
    if archive == GAME:
        return 'game/df_game_r' if name == 'df_game_r' else None
    if archive == FRIDA:
        prefix = 'home/neople/game/'
        return 'frida/' + name[len(prefix):] if name.startswith(prefix) and name[len(prefix):] in FRIDA_FILES else None
    if archive == DP2:
        if not name.startswith('dp2/') or name.lower().endswith('.log'):
            return None
        if name.lower().endswith('.zip'):
            return 'archive/dp2-pvf-patch.zip'
        return name
    if archive == SERVER and name in SERVER_FILES:
        return 'server/' + name
    return None


def put_file(stage, relative, data, records, source_id, member, original_hash=None):
    path = stage.joinpath(*PurePosixPath(relative).parts)
    if path.exists():
        raise ValueError('duplicate output path: ' + relative)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)
    mode = 0o755 if relative == 'game/df_game_r' or relative.startswith('server/') or relative.endswith('.sh') else 0o644
    path.chmod(mode)
    os.utime(path, (0, 0))
    row = dict(path=relative, size=len(data), sha256=hashlib.sha256(data).hexdigest(),
               mode=oct(mode), source=source_id, member=member)
    if original_hash and row['sha256'] != original_hash:
        row['original_sha256'] = original_hash
    records.append(row)


def import_archive(source, archive, stage, records, excluded):
    seen = set()
    total = 0
    with tarfile.open(source / archive, 'r:gz') as stream:
        members = stream.getmembers()
        if len(members) > MAX_MEMBERS:
            raise ValueError('archive member limit exceeded')
        for member in members:
            name = safe_name(member.name)
            key = name.casefold()
            if key in seen:
                raise ValueError('duplicate archive member: ' + repr(name))
            seen.add(key)
            if not member.isfile() and not member.isdir():
                raise ValueError('links/devices are not allowed: ' + repr(name))
            total += member.size
            if member.size < 0 or total > MAX_TOTAL_BYTES:
                raise ValueError('archive size limit exceeded')
        for member in sorted(members, key=lambda m: m.name):
            if member.isdir():
                continue
            name = safe_name(member.name)
            relative = destination(archive, name)
            if relative is None:
                excluded.append({'source': archive, 'member': name})
                continue
            with stream.extractfile(member) as content:
                data = content.read()
            original_hash = hashlib.sha256(data).hexdigest()
            if relative == 'dp2/lua/df/game/report.lua':
                data = REPORT_STUB
            elif relative in ('dp2/script/delete_creature_item.sh', 'dp2/script/covert_at_swordman.sh'):
                data = shell_helper(PurePosixPath(relative).name)
            put_file(stage, relative, data, records, archive, name, original_hash)


def tree_hashes(root):
    result = {}
    for path in root.rglob('*'):
        if path.is_symlink():
            raise ValueError('output contains symlink: ' + str(path))
        if path.is_file():
            result[path.relative_to(root).as_posix()] = sha256(path)
    return result


def prepare(source, output, include_server=False, plugins_only=False):
    source = source.resolve()
    if output.is_symlink():
        raise ValueError('output must not be a symlink')
    output = output.absolute()
    if source == output.resolve() or source in output.resolve().parents:
        raise ValueError('output must be outside source archive directory')
    inputs = verified_sources(source, include_server, plugins_only)
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='.vipai86-', dir=output.parent) as temp:
        stage = Path(temp) / 'overlay'
        stage.mkdir()
        records, excluded = [], []
        for row in inputs:
            if row['id'] != SQL:
                import_archive(source, row['id'], stage, records, excluded)
        put_file(stage, 'seed/vipai.sql', (source / SQL).read_bytes(), records, SQL, SQL)
        required = {'game/df_game_r', 'dp2/libdp2.so', 'dp2/df_game_r.lua',
                    'dp2/lua/df/game/report.lua'} | {'frida/' + name for name in FRIDA_FILES}
        if plugins_only:
            required.remove('game/df_game_r')
        missing = required - {row['path'] for row in records}
        if missing:
            raise ValueError('missing required assets: ' + ', '.join(sorted(missing)))
        manifest = {'schema_version': 1, 'profile': 'ordinary-dp2-frida' if plugins_only else 'vipai86-ordinary-dp2-frida',
                    'sources': inputs, 'files': sorted(records, key=lambda row: row['path']),
                    'excluded': sorted(excluded, key=lambda row: (row['source'], row['member'])),
                    'changes': ['DP2 report.create/send/close replaced with no-network no-op',
                                'DP2 SQL helpers validate numeric arguments and use runtime MYSQL_PWD',
                                'Historical logs omitted; PVF ZIP kept as an unapplied archive'],
                    'runtime_requirements': ['Render frida_config.json db_config from runtime secrets',
                                             'Validate game, DP2 and Frida fixed-address ABI together before enabling hooks',
                                             'Binary code has not been executed or exhaustively audited']}
        metadata = stage / 'manifest.json'
        metadata.write_bytes((json.dumps(manifest, indent=2, sort_keys=True, ensure_ascii=True) + '\n').encode('utf-8'))
        os.utime(metadata, (0, 0))
        if verified_sources(source, include_server, plugins_only) != inputs:
            raise ValueError('sources changed during import')
        if output.exists():
            if not output.is_dir() or tree_hashes(output) != tree_hashes(stage):
                raise ValueError('existing output differs; choose a new --output for review')
        else:
            stage.rename(output)
    return len(records)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, default=ROOT.parent / 'resource-archive')
    parser.add_argument('--output', type=Path, default=ROOT / 'services/game/vipai86-assets')
    parser.add_argument('--include-server', action='store_true', help='include allowlisted upstream service executables only')
    parser.add_argument('--plugins-only', action='store_true', help='prepare shared plugins without a level-specific executable')
    args = parser.parse_args()
    try:
        count = prepare(args.source, args.output, args.include_server, args.plugins_only)
    except (ValueError, OSError, KeyError, tarfile.TarError) as error:
        print('Asset import failed: ' + str(error), file=sys.stderr)
        return 1
    print('Verified %d overlay files: %s' % (count, args.output))
    return 0


if __name__ == '__main__':
    sys.exit(main())
