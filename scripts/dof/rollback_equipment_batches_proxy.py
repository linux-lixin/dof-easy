"""Authenticated frozen batch receipts for a pinned, write-blocked old admin.

Does not execute commands or fetch data. Current/native state and task execution
are deliberately unavailable; only explicitly snapshotted historical queries work.
"""
import hashlib
import re
import uuid
from urllib.parse import urlsplit

GUARD_SHA256 = '6171aae17e626f65c82c525107d2a9f088ea3a7d32040af14ec02af53220a282'
ID = r'[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}'
PREFIX = r'/api/v1/admin/activities/(inheritance|recovery|cross-border)/batches'
ROUTE = re.compile('^'+PREFIX+r'(?:/('+ID+r')(?:/controls(?:/('+ID+r'))?)?)?$')


def render(source, manifest):
    if hashlib.sha256(source).hexdigest() != GUARD_SHA256:
        raise ValueError('Requires pinned old proxy with all business writes blocked')
    if manifest.get('schema') != 1 or manifest.get('module') != 'equipment-batches' or manifest.get('instanceId') != 'dof-main':
        raise ValueError('Unsupported isolated batch snapshot')
    if str(uuid.UUID(manifest.get('databaseUuid', ''))) != manifest['databaseUuid']:
        raise ValueError('Invalid database identity')
    if not re.fullmatch('[0-9a-f]{40}', manifest.get('sourceCommit', '')):
        raise ValueError('Missing immutable source identity')
    routes = manifest.get('routes')
    if not isinstance(routes, dict) or not 2 <= len(routes) <= 1001:
        raise ValueError('Missing or oversized batch snapshot')
    mapped, paths = [], set()
    for target, record in sorted(routes.items()):
        parts = urlsplit(target)
        match = ROUTE.fullmatch(parts.path)
        if not match or parts.scheme or parts.netloc or parts.fragment:
            raise ValueError('Only fixed historical batch routes may be frozen')
        if match.group(2) and str(uuid.UUID(match.group(2))) != match.group(2):
            raise ValueError('Invalid task identity')
        if match.group(3) and str(uuid.UUID(match.group(3))) != match.group(3):
            raise ValueError('Invalid control identity')
        if parts.query:
            if not match.group(2) or match.group(3) or not re.fullmatch(r'page=[1-9][0-9]{0,8}&pageSize=20', parts.query):
                raise ValueError('Only explicit canonical receipt pages may be frozen')
        if target != parts.path + ('?' + parts.query if parts.query else ''):
            raise ValueError('Noncanonical snapshot URL')
        filename = hashlib.sha256(target.encode()).hexdigest() + '.json'
        if not isinstance(record, dict) or record.get('file') != filename or not re.fullmatch('[0-9a-f]{64}', record.get('sha256', '')):
            raise ValueError('Invalid fixed receipt file')
        paths.add(parts.path)
        mapped.append('        "%s?%s" %s;' % (parts.path, parts.query, filename))
    if not any(ROUTE.fullmatch(p).group(2) is None for p in paths) or not any(ROUTE.fullmatch(p).group(2) for p in paths):
        raise ValueError('Both task catalog and task receipt are required')
    # Task/control URLs contain two UUIDs. Nginx's default 64-byte buckets
    # reject these valid keys at startup, before any backend can become ready.
    policy = '\n    map_hash_bucket_size 256;\n    map_hash_max_size 8192;\n    map "$uri?$args" $frozen_batch_file {\n        default "";\n' + '\n'.join(mapped) + '\n    }\n'
    locations = ['''        location = /__dof_batch_rollback_auth {
            internal;
            proxy_pass http://127.0.0.1:${SERVER_PORT}/api/v1/admin/dof;
            proxy_pass_request_body off;
            proxy_set_header Content-Length "";
            proxy_set_header Authorization $http_authorization;
            proxy_set_header Host $http_host;
        }
        # Live state and execution cannot be supplied by historical snapshots.
        location ~ ^/api/v1/admin/activities/(inheritance|recovery|cross-border)/batches(?:/|$) {
            return 503;
        }
''']
    for path in sorted(paths):
        locations.append('''        location = %s {
            if ($request_method !~ ^(GET|HEAD)$) { return 503; }
            if ($frozen_batch_file = "") { return 400; }
            auth_request /__dof_batch_rollback_auth;
            root /app/rollback-equipment-batches;
            try_files /$frozen_batch_file =404;
            default_type application/json;
            add_header Cache-Control "no-store" always;
            add_header X-Dof-Rollback-Receipts "frozen-batch-history" always;
        }
''' % path)
    text = source.decode()
    marker = '        location / {\n'
    if text.count('http {') != 1 or text.count(marker) != 1:
        raise ValueError('Unknown old proxy layout')
    return text.replace('http {', 'http {\n'+policy, 1).replace(marker, ''.join(locations)+marker, 1).encode()
