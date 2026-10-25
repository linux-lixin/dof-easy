"""Repository CLI for the same deterministic bridge repair used by the image build."""
from pathlib import Path
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from services.game.runtime.ubuntu.bridge_compat import BRIDGE_PIN, derive, main

if __name__ == '__main__':
    main()
