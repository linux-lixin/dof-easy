"""Exercise real Docker pulls and Compose resolution against a loopback fixture.

Creates four empty test images through a temporary, read-only Registry V2 HTTP
fixture. No production images are uploaded, built, started or changed.
Run explicitly: python tests/integration/test_registry_images.py
"""
import argparse
import hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import uuid

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('registry_manager', ROOT / 'scripts/container/manage.py')
manager = importlib.util.module_from_spec(spec)
spec.loader.exec_module(manager)


def encoded(value):
    return json.dumps(value, separators=(',', ':')).encode()


def digest(data):
    return 'sha256:' + hashlib.sha256(data).hexdigest()


def registry_server(host='127.0.0.1', port=0):
    records = {}
    for role in manager.image_config.ROLES:
        config = encoded({'architecture': 'amd64', 'os': 'linux',
                          'config': {'Labels': {'dof-easy.test': 'registry-fixture', 'role': role}},
                          'rootfs': {'type': 'layers', 'diff_ids': []}, 'history': []})
        manifest = encoded({'schemaVersion': 2,
                            'mediaType': 'application/vnd.docker.distribution.manifest.v2+json',
                            'config': {'mediaType': 'application/vnd.docker.container.image.v1+json',
                                       'size': len(config), 'digest': digest(config)}, 'layers': []})
        records[role] = (config, manifest)

    class Handler(BaseHTTPRequestHandler):
        protocol_version = 'HTTP/1.1'

        def send_error(self, *args, **kwargs):
            # Docker probes HTTPS before retrying HTTP for loopback registries.
            # Even a TLS ClientHello rejected by the HTTP parser needs a proper
            # HTTP status line, not BaseHTTPRequestHandler's HTTP/0.9 body.
            self.request_version = 'HTTP/1.1'
            super().send_error(*args, **kwargs)

        def log_message(self, *_):
            pass

        def do_HEAD(self):
            self.serve(False)

        def do_GET(self):
            self.serve(True)

        def serve(self, body):
            path = self.path.split('?', 1)[0]
            data, media = b'{}', 'application/json'
            if path not in ('/v2', '/v2/'):
                match = None
                for role, (config, manifest) in records.items():
                    prefix = '/v2/check/dof-easy-' + role
                    if path in (prefix + '/manifests/test', prefix + '/manifests/' + digest(manifest)):
                        match = role
                        data, media = manifest, 'application/vnd.docker.distribution.manifest.v2+json'
                    elif path == prefix + '/blobs/' + digest(config):
                        match, data = role, config
                if match is None:
                    self.send_error(404)
                    return
            self.send_response(200)
            self.send_header('Docker-Distribution-Api-Version', 'registry/2.0')
            self.send_header('Content-Type', media)
            self.send_header('Content-Length', str(len(data)))
            self.send_header('Docker-Content-Digest', digest(data))
            self.end_headers()
            if body:
                self.wfile.write(data)

    return ThreadingHTTPServer((host, port), Handler)


def main():
    # Run the fixture in Docker's network environment as well: on Docker
    # Desktop the daemon's loopback is not the Python host's loopback.
    name = 'dof-registry-test-' + uuid.uuid4().hex[:10]
    fixture_image = os.environ.get('DOF_REGISTRY_TEST_IMAGE', 'dof-easy-game:local')
    references = {}
    try:
        subprocess.run(['docker', 'run', '-d', '--rm', '--pull', 'never', '--name', name,
                        '--network', 'host', '--read-only', '--cap-drop', 'ALL',
                        '--security-opt', 'no-new-privileges',
                        '--mount', 'type=bind,source=' + str(ROOT) + ',target=/workspace,readonly',
                        '-w', '/workspace', '--entrypoint', 'python3', fixture_image,
                        '-B', 'tests/integration/test_registry_images.py', '--serve'],
                       check=True, capture_output=True, timeout=30)
        port = ''
        for _ in range(50):
            port = subprocess.check_output(['docker', 'logs', name], text=True).strip()
            if port.isdigit():
                break
            time.sleep(0.1)
        if not port.isdigit():
            raise RuntimeError('Loopback registry did not start')
        settings = {'registry': '127.0.0.1:' + port + '/check', 'tag': 'test'}
        references = manager.image_config.references(settings)
        subprocess.run(['docker', 'exec', name, 'python3', '-c',
                        'import urllib.request; urllib.request.urlopen("http://127.0.0.1:' + port + '/v2/", timeout=10).read()'],
                       check=True, capture_output=True, timeout=20)
        manager.acquire_images(settings, 'pull')
        with tempfile.TemporaryDirectory(prefix='.registry-check-', dir=ROOT) as directory:
            instance = Path(directory) / 'registry-check'
            instance.mkdir()
            args = argparse.Namespace(ip='127.0.0.1', bind='127.0.0.1', channels='15,52',
                                      admin_port=18080, plugins='none')
            values = manager.initial_values(args, {'level': 70, 'game': {'sha256': 'a' * 64}}, instance)
            values.update(manager.image_config.environment(settings))
            manager.write_env(instance / '.env', values)
            manager.write_json(instance / 'channels.json', manager.ports_override(values))
            model = json.loads(manager.compose(instance, 'config', '--format', 'json', capture_output=True, text=True).stdout)
            expected = dict(db='db', admin='admin', keys='admin', game='game', ingress='game', netguard='netguard')
            for service, role in expected.items():
                assert model['services'][service]['image'] == references[role], service
                assert 'build' not in model['services'][service], service
                assert model['services'][service]['pull_policy'] == 'never', service
        print('PASS: real Docker pulled all four loopback images; every Compose role uses configured images with no build definitions')
    finally:
        subprocess.run(['docker', 'rm', '-f', name], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        if references:
            subprocess.run(['docker', 'image', 'rm', *references.values()], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


if __name__ == '__main__':
    if '--serve' in sys.argv:
        server = registry_server()
        print(server.server_port, flush=True)
        server.serve_forever()
    else:
        main()
