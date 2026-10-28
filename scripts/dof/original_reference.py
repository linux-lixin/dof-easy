"""Export the source-locked original gateway schema for an isolated reference.

This never connects to a database or executes the gateway. The exported SQL is
only for a new, disposable reference database, not for migrating a live server.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))
from scripts.dof.native_candidates import GATEWAY_PIN, replacement_source

BASE_SCHEMA_PIN = '440604c1e7e786012abab3e69efac077a48da9d7ceae1ab43394a8eed4bc504b'


def extract(source):
    if hashlib.sha256(source).hexdigest() != GATEWAY_PIN:
        raise ValueError('Expected the pinned original replacement gateway')
    anchor = source.index(b'CREATE TABLE `billing`')
    block = source[source.rfind(b'\0', 0, anchor) + 1:source.index(b'\0', anchor)]
    if hashlib.sha256(block).hexdigest() != BASE_SCHEMA_PIN:
        raise ValueError('Original embedded schema hash differs from the source lock')
    statements = [s.strip() for s in block.decode('utf-8').split(';') if s.strip()]
    # The original block contains a DROP for fightingcapacity. Keep its hash as
    # evidence, but never export a destructive initializer for an existing DB.
    skipped = []
    kept = []
    tables = []
    for statement in statements:
        if statement.startswith('CREATE DATABASE ') or statement == 'USE qk':
            skipped.append(statement)
        elif statement == 'DROP TABLE IF EXISTS `fightingcapacity`':
            skipped.append(statement)
        elif statement.startswith('CREATE TABLE '):
            match = re.match(r'CREATE TABLE\s+(?:`qk`\.)?`([A-Za-z_][A-Za-z_0-9]*)`', statement)
            if not match:
                raise ValueError('Unreviewed original table declaration')
            tables.append(match[1])
            kept.append(statement)
        elif statement.startswith('INSERT INTO '):
            kept.append(statement)
        else:
            raise ValueError('Unreviewed original initializer statement')
    if len(tables) != 20 or len(set(tables)) != 20:
        raise ValueError('Expected twenty original base tables')
    if not {'qingkong_config', 'qingkong_config_v2', 'billing', 'regCdkey'} <= set(tables):
        raise ValueError('Original identifiers were lost')
    # CREATE DATABASE without IF NOT EXISTS rejects any pre-existing qk schema.
    # Callers must additionally verify their isolated server UUID before import.
    sql = ('CREATE DATABASE `qk` DEFAULT CHARACTER SET utf8;\nUSE `qk`;\n'
           + ';\n'.join(kept) + ';\n').encode('utf-8')
    report = dict(sourceGatewaySHA256=GATEWAY_PIN, embeddedSchemaSHA256=BASE_SCHEMA_PIN,
                  exportedSqlSHA256=hashlib.sha256(sql).hexdigest(), database='qk',
                  tables=tables, omittedOriginalStatements=skipped,
                  nativeNamesPreserved=True, seedDataOrigin='pinned gateway defaults',
                  databaseAccessPerformed=False, gatewayExecuted=False,
                  originalGuiLoginVerified=False, fullReferenceEnvironmentReady=False,
                  limitations=['Base schema only; runtime-created feature tables require separate evidence.',
                               'Import only into a verified isolated server with no qk database.',
                               'This export does not establish original GUI or game compatibility.'])
    return sql, report


def export(gateway, output):
    source = replacement_source(Path(gateway), GATEWAY_PIN)
    sql, report = extract(source)
    output = Path(output)
    output.mkdir(parents=False, exist_ok=False)
    (output / 'original-base.sql').write_bytes(sql)
    (output / 'manifest.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--gateway', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(export(args.gateway, args.output), ensure_ascii=False, indent=2))
