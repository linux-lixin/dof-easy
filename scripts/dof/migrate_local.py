"""Explicit offline migration of a copied native state and SQL exports.

The source is a native-state directory, not a live instance or database server.
It must contain the pinned replacement server files, config/, optional keys and
optional database/*.sql exports. Output contains state/ for /data/dof-easy, plus separate SQL and audit materials.
No connections or native sample execution occur here.
"""
import argparse
import configparser
import hashlib
import json
from pathlib import Path
import re
import shutil
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(ROOT / 'services/game/runtime/ubuntu'))
from gateway import assets
from scripts.dof.native_candidates import SQL_TABLE_NAMES

NAMES = {'qk': 'dof_native', 'qingkong_config': 'config', 'qingkong_config_v2': 'config_v2', **SQL_TABLE_NAMES}
CONFIGS = {'qk_plugin_config.ini': 'plugin.ini', 'qk_plugin_antonRaid.ini': 'anton-raid.ini',
           'qk_plugin_lukeRaid.ini': 'luke-raid.ini', 'config.ini': 'gateway.ini', 'dungeon.lst': 'dungeon.lst'}


def digest(path):
    with path.open('rb') as f:
        return hashlib.file_digest(f, 'sha256').hexdigest()


def convert_sql(text):
    """Rename SQL identifiers while retaining quoted data, passwords and comments."""
    if re.search(r'\bANSI_QUOTES\b|\bNO_BACKSLASH_ESCAPES\b', text, re.I):
        raise ValueError('Export uses an unsupported SQL quoting mode')
    pattern = re.compile(r"('(?:\\.|''|[^'\\])*'|\"(?:\\.|\"\"|[^\"\\])*\"|`(?:``|[^`])*`|/\*[\s\S]*?\*/|--[^\n]*|\#[^\n]*|[A-Za-z_][A-Za-z_0-9]*|[^'\"`A-Za-z_])")
    parts, changes, end = [], {}, 0
    for m in pattern.finditer(text):
        if m.start() != end:
            raise ValueError('Malformed or unterminated SQL token')
        end = m.end(); token = m[0]; new = token
        if token.startswith('/*!'):
            version = re.match(r'/\*!\d*\s*', token)[0]
            inner, nested = convert_sql(token[len(version):-2])
            new = version + inner + '*/'
            for key, count in nested.items(): changes[key] = changes.get(key, 0) + count
        elif token.startswith('`'):
            key = token[1:-1].replace('``', '`')
            if key in NAMES:
                new = '`' + NAMES[key] + '`'; changes[key] = changes.get(key, 0) + 1
        elif token in NAMES:
            new = NAMES[token]; changes[token] = changes.get(token, 0) + 1
        parts.append(new)
    if end != len(text):
        raise ValueError('Malformed SQL export')
    return ''.join(parts), changes


def migrate(source, output, runtime, dry_run=False):
    source, output = Path(source).absolute(), Path(output).absolute()
    if source != source.resolve() or not source.is_dir():
        raise ValueError('Source must be an existing real local directory')
    if output.exists() or output.is_symlink() or output.is_relative_to(source) or source.is_relative_to(output):
        raise ValueError('Output must be new and separate from the source')
    if output.parent != output.parent.resolve():
        raise ValueError('Output ancestors must not be links')
    files = sorted(p for p in source.rglob('*') if not p.is_dir())
    if any(p.is_symlink() for p in source.rglob('*')) or any(not p.is_file() for p in files):
        raise ValueError('Source must contain only local regular files and directories')
    before = {p.relative_to(source).as_posix(): digest(p) for p in files}
    rows = json.loads((ROOT / 'metadata/dof/source-audit/server-inputs/replacement.lock.json').read_text())['files']
    for row in rows:
        if before.get(row['path']) != row['sha256']:
            raise ValueError('Migration requires the pinned replacement input: ' + row['path'])
    assets.verify(runtime)
    planned, sql_changes = {}, {}
    for old, new in CONFIGS.items():
        options = [source / 'config' / old, source / old]
        found = [p for p in options if p.is_file()]
        if len(found) > 1:
            raise ValueError('Ambiguous duplicate configuration: ' + old)
        if not found:
            if old.startswith('qk_plugin_'):
                raise ValueError('Missing original configuration: ' + old)
            continue
        data = found[0].read_bytes()
        if new == 'gateway.ini':
            parser = configparser.ConfigParser(interpolation=None); parser.optionxform = str
            parser.read_string(data.decode('utf-8-sig'))
            parser['Gate']['DfGameR'] = '/home/neople/game/df_game_r'
            parser['sever']['LogPath'] = '/var/log/dof-easy/'
            import io
            buffer = io.StringIO(); parser.write(buffer, space_around_delimiters=False)
            data = buffer.getvalue().encode()
        planned['config/' + new] = data
    for name in ['privatekey.pem', 'publickey.pem']:
        found = [p for p in [source / name, source / 'keys' / name] if p.is_file()]
        if len(found) > 1: raise ValueError('Ambiguous duplicate key: ' + name)
        if found: planned['keys/' + name] = found[0].read_bytes()
    for path in (source / 'database').glob('*') if (source / 'database').is_dir() else []:
        if path.suffix != '.sql' or not path.is_file():
            raise ValueError('Database input must be SQL exports, not live database files')
        text = path.read_text(encoding='utf-8-sig')
        converted, changed = convert_sql(text)
        target = 'database/' + ('dof_native.sql' if path.name == 'qk.sql' else path.name)
        if target in planned: raise ValueError('Database output name collision')
        planned[target] = converted.encode(); sql_changes[target] = changed
    if (source / '.env').exists():
        env = {}
        for line in (source / '.env').read_text().splitlines():
            if not line or line.startswith('#'): continue
            name, value = line.split('=', 1)
            if name in env: raise ValueError('Duplicate environment key')
            env[name] = value
        if env.pop('QINGKONG_VARIANT', 'replacement') != 'replacement':
            raise ValueError('Authorized-edition instances cannot be migrated by this tool')
        if env.get('LAUNCHER_MODE') == 'qingkong': env['LAUNCHER_MODE'] = 'native'
        assets.profile_for(env)
        planned['instance.env'] = ''.join(k + '=' + v + '\n' for k, v in env.items()).encode()
    report = dict(schema=1, dryRun=dry_run, serverAccessed=False, sourceUnchanged=False,
                  files={name: dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest()) for name, raw in planned.items()},
                  sourceHashes=before, sqlIdentifierChanges=sql_changes,
                  databaseImportVerified=False, businessRowsVerified=False,
                  releaseReady=False, scope='Native state and SQL-export conversion; no live instance migration')
    def unchanged():
        after = {p.relative_to(source).as_posix(): digest(p) for p in source.rglob('*') if p.is_file()}
        if after != before: raise ValueError('Source changed during migration')
        report['sourceUnchanged'] = True
    if dry_run:
        unchanged(); return report
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='.dof-migrate-', dir=output.parent) as temporary:
        stage = Path(temporary) / 'bundle'; stage.mkdir(mode=0o700)
        assets.install(runtime, stage / 'state')
        for name, raw in planned.items():
            p = (stage / 'state' if name.startswith(('config/', 'keys/')) else stage) / name
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_bytes(raw); p.chmod(0o600)
        unchanged()
        (stage / 'source-audit').mkdir(mode=0o700)
        (stage / 'source-audit/migration.json').write_text(json.dumps(report, indent=2) + '\n')
        (stage / 'source-audit/migration.json').chmod(0o600)
        if output.exists() or output.is_symlink(): raise ValueError('Output appeared during migration')
        stage.rename(output)
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--runtime', type=Path, default=ROOT / '.dof-easy/server-runtime/current')
    parser.add_argument('--dry-run', action='store_true')
    args = parser.parse_args()
    report = migrate(args.source, args.output, args.runtime, args.dry_run)
    print('Validated offline migration of', len(report['files']), 'files;',
          'dry run only' if args.dry_run else 'new state written; SQL import still requires verification')

if __name__ == '__main__':
    main()
