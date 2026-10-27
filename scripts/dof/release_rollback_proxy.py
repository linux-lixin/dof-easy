"""Render a pinned old admin proxy for a controlled read-only rollback.

This only creates an operator-reviewed build input. It does not run Docker,
connect to a server or make an old reader understand new operation journals.
Only GET/HEAD/OPTIONS and the exact login POST pass the HTTP ingress.
"""
import argparse
import hashlib
from pathlib import Path


def render(source, expected_sha256):
    if hashlib.sha256(source).hexdigest() != expected_sha256:
        raise ValueError('Rollback proxy source differs from the pinned old image')
    text = source.decode('utf-8')
    if text.count('http {') != 1 or text.count('    server {') != 1:
        raise ValueError('Unsupported old proxy layout')
    if 'rollback_write_blocked' in text:
        raise ValueError('Rollback guard is already present')
    policy = '''
    # Old readers cannot interpret new cancelled/prepared intents.
    # Keep all business mutations closed; login only creates an auth session.
    map "$request_method:$uri" $rollback_write_blocked {
        default 1;
        ~^(GET|HEAD|OPTIONS): 0;
        "POST:/api/v1/login" 0;
    }
'''
    text = text.replace('http {', 'http {\n' + policy, 1)
    text = text.replace('    server {', '    server {\n        if ($rollback_write_blocked) { return 503; }', 1)
    return text.encode('utf-8')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--source-sha256', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    value = render(args.source.read_bytes(), args.source_sha256)
    with args.output.open('xb') as stream:
        stream.write(value)
