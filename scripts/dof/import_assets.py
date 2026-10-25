#!/usr/bin/env python3
"""Import only pinned local replacement server inputs into a derived bundle."""
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from scripts.dof.build_server_runtime import build, main

if __name__ == '__main__':
    main()
