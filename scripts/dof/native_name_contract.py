"""Load the manifest-bound module assertions used by native loading tests."""
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def module_contract(runtime):
    contract = json.loads((ROOT / 'metadata/dof/native-name-contract.json').read_text())
    manifest = Path(runtime) / 'manifest.json'
    if contract.get('schema') != 1 or hashlib.sha256(manifest.read_bytes()).hexdigest() != contract['runtimeManifestSha256']:
        raise ValueError('Native name contract does not match runtime manifest')
    return contract['modules']
