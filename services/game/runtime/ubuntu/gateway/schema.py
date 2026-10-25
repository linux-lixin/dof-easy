"""Initialize missing native tables from the installed, hash-pinned schema."""
import hashlib
import os
from pathlib import Path
import re
import subprocess

def definitions(schema):
    from gateway.assets import manifest
    raw = Path(schema).read_bytes()
    row, = (row for row in manifest() if row['path'] == 'runtime/gateway/schema.sql')
    if hashlib.sha256(raw).hexdigest() != row['sha256']:
        raise ValueError('Native schema differs from the installed runtime lock')
    # This exact, pinned block has no semicolons inside quoted values.
    creates, seeds = {}, {}
    for statement in raw.decode('utf-8').split(';'):
        statement = statement.strip()
        create = re.match(r'CREATE TABLE\s+(?:`dof_native`\.)?`([A-Za-z0-9_]+)`', statement)
        seed = re.match(r'INSERT INTO `([A-Za-z0-9_]+)`', statement)
        if create:
            creates[create[1]] = statement
        elif seed:
            seeds[seed[1]] = statement
        # Deliberately do not execute the embedded DROP or blanket init block.
    if len(creates) != 20 or set(seeds) != {'billing', 'robot', 'titles', 'config', 'config_v2'}:
        raise ValueError('Unexpected Dof base schema layout')
    return creates, seeds


def initialize(binary, query):
    creates, seeds = definitions(binary)
    query('CREATE DATABASE IF NOT EXISTS dof_native CHARACTER SET utf8')
    existing = set(query("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='dof_native'").splitlines())
    created = []
    for table, statement in creates.items():
        if table in existing:
            continue
        query('USE dof_native;' + statement)
        created.append(table)
        if table in seeds:
            query('USE dof_native;' + seeds[table])
    return created


def query(statement):
    result = subprocess.run(
        ['/usr/local/bin/mysql', '-h', os.environ['CUR_SG_DB_HOST'],
         '-P', os.environ['CUR_SG_DB_PORT'], '-ugame', '-N', '--default-character-set=utf8'],
        input=(statement + ';\n').encode(),
        env=dict(os.environ, MYSQL_PWD=os.environ['DOF_DB_GAME_PASSWORD']),
        capture_output=True, timeout=30)
    if result.returncode:
        code = re.search(rb'ERROR (\d+)', result.stderr)
        raise RuntimeError('Dof base schema initialization failed (MySQL ' +
                           (code[1].decode() if code else 'unknown error') + ')')
    return result.stdout.decode('utf-8').strip()


def prepare(binary):
    created = initialize(binary, query)
    print('[Dof] base schema checked; created ' + str(len(created)) + ' missing tables', flush=True)
