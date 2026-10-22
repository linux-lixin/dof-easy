"""Registry settings shared by installation, source builds and backup helpers."""
import json
import os
from pathlib import Path
import re
import tempfile

ROLES = ('db', 'admin', 'game', 'netguard')
KEYS = {role: 'DOF_' + role.upper() + '_IMAGE' for role in ROLES}


def validate(registry, tag, allow_local=False):
    if not isinstance(registry, str) or not isinstance(tag, str):
        raise ValueError('镜像仓库和标签必须是字符串。')
    registry = registry.rstrip('/')
    if not registry:
        if not allow_local:
            raise ValueError('尚未配置镜像仓库。运行 dof.sh images --registry 仓库地址/命名空间 --tag 版本；源码构建请显式加 --build。')
    else:
        parts = registry.split('/')
        host, _, port = parts[0].partition(':')
        if (any(not re.fullmatch(r'[a-z0-9](?:[a-z0-9-]*[a-z0-9])?', label) for label in host.split('.'))
                or not ('.' in host or ':' in parts[0] or host == 'localhost')
                or (':' in parts[0] and (not port.isdigit() or not 1 <= int(port) <= 65535))
                or any(not re.fullmatch(r'[a-z0-9]+(?:(?:[._]|__|-+)[a-z0-9]+)*', part) for part in parts[1:])):
            raise ValueError('镜像仓库格式应为 ghcr.io/用户名 或 registry.example.com:5000/项目，不包含协议、密码或镜像名。')
    if not re.fullmatch(r'[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}', tag):
        raise ValueError('镜像标签格式无效。')
    return {'registry': registry, 'tag': tag}


def load(state):
    path = state / 'images.json'
    if not path.exists():
        return {}
    data = json.loads(path.read_text(encoding='utf-8'))
    if not isinstance(data, dict) or set(data) != {'registry', 'tag'}:
        raise ValueError('镜像配置文件应只包含 registry 和 tag。')
    return validate(data['registry'], data['tag'])


def resolve(args, defaults, existing=None, allow_local=False):
    existing = existing or {}
    registry = getattr(args, 'registry', None)
    if registry is None:
        registry = existing.get('IMAGE_REGISTRY', defaults.get('registry', ''))
    tag = getattr(args, 'tag', None)
    if tag is None:
        tag = existing.get('IMAGE_TAG', defaults.get('tag', 'latest' if registry else 'local'))
    return validate(registry, tag, allow_local)


def references(settings):
    prefix = settings['registry'] + '/' if settings['registry'] else ''
    return {role: prefix + 'dof-easy-' + role + ':' + settings['tag'] for role in ROLES}


def environment(settings):
    return dict(IMAGE_REGISTRY=settings['registry'], IMAGE_TAG=settings['tag'],
                **{KEYS[role]: ref for role, ref in references(settings).items()})


def instance_reference(values, role):
    # Instances created before configurable registries keep their local tags.
    return values.get(KEYS[role], 'dof-easy-' + role + ':local')


def save(state, settings):
    settings = validate(settings['registry'], settings['tag'])
    state.mkdir(parents=True, exist_ok=True)
    fd, name = tempfile.mkstemp(prefix='.images-', dir=state)
    temporary = Path(name)
    try:
        with os.fdopen(fd, 'w', encoding='utf-8', newline='\n') as stream:
            json.dump(settings, stream, indent=2)
            stream.write('\n')
        temporary.chmod(0o600)
        temporary.replace(state / 'images.json')
    finally:
        temporary.unlink(missing_ok=True)
