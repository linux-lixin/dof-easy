"""Locate provenance outside deployable resources, or inside explicit imported bundles."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def derivation_path(runtime):
    runtime = Path(runtime).resolve(strict=True)
    if runtime == (ROOT / 'services/game/resources/native').resolve():
        return ROOT / 'metadata/dof/source-audit/server-derived/derivation.json'
    path = runtime / 'source-audit/derivation.json'
    if path.is_symlink() or not path.is_file():
        raise ValueError('Derived bundle requires its own provenance record')
    return path
