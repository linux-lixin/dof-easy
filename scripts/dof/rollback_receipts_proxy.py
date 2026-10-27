"""Add authenticated, frozen equipment receipts to an already guarded old proxy.

No business executor, server connection or credential input. Only exact history
GET/HEAD routes are served; the old backend rechecks admin access on every read.
"""
import hashlib
import re
import uuid

GUARD_SHA256 = '6171aae17e626f65c82c525107d2a9f088ea3a7d32040af14ec02af53220a282'
BASE = '/api/v1/admin/activities/cross-border/history'


def render(source, manifest):
    if hashlib.sha256(source).hexdigest() != GUARD_SHA256:
        raise ValueError('Requires the pinned read-only old proxy')
    if manifest.get('schema') != 1 or manifest.get('module') != 'cross-border':
        raise ValueError('Unsupported receipt snapshot')
    if manifest.get('instanceId') != 'dof-main':
        raise ValueError('Snapshot must name the isolated instance')
    if str(uuid.UUID(manifest.get('databaseUuid', ''))) != manifest['databaseUuid']:
        raise ValueError('Invalid snapshot database identity')
    if not re.fullmatch('[0-9a-f]{40}', manifest.get('sourceCommit', '')):
        raise ValueError('Missing snapshot source identity')
    routes = manifest.get('routes')
    if not isinstance(routes, dict) or BASE not in routes or not 2 <= len(routes) <= 501:
        raise ValueError('Incomplete or oversized snapshot')
    locations = ['''        # Frozen receipts bridge; backend authorization remains live.
        location = /__dof_rollback_receipts_auth {
            internal;
            proxy_pass http://127.0.0.1:${SERVER_PORT}/api/v1/admin/dof;
            proxy_pass_request_body off;
            proxy_set_header Content-Length "";
            proxy_set_header Authorization $http_authorization;
            proxy_set_header Host $http_host;
        }
''']
    for route, value in sorted(routes.items()):
        if route != BASE:
            if not route.startswith(BASE + '/'):
                raise ValueError('Snapshot route outside fixed history namespace')
            parts = route[len(BASE) + 1:].split('/')
            if len(parts) > 2 or len(parts) == 2 and parts[1] != 'cancellations':
                raise ValueError('Unsupported history read route')
            request_id = parts[0]
            if str(uuid.UUID(request_id)) != request_id:
                raise ValueError('Invalid receipt route')
        expected_name = hashlib.sha256(route.encode()).hexdigest() + '.json'
        if not isinstance(value, dict) or value.get('file') != expected_name or not re.fullmatch('[0-9a-f]{64}', value.get('sha256', '')):
            raise ValueError('Invalid fixed snapshot file')
        locations.append('''        location = %s {
            if ($request_method !~ ^(GET|HEAD)$) { return 503; }
            auth_request /__dof_rollback_receipts_auth;
            default_type application/json;
            add_header Cache-Control "no-store" always;
            add_header X-Dof-Rollback-Receipts "frozen-read-only" always;
            alias /app/rollback-receipts/%s;
        }
''' % (route, expected_name))
    text = source.decode('utf-8')
    marker = '        location / {\n'
    if text.count(marker) != 1:
        raise ValueError('Unknown proxy server layout')
    return text.replace(marker, ''.join(locations) + marker, 1).encode()
