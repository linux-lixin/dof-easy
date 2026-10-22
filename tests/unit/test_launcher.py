"""Launcher regressions: compile/run production configuration code on Windows."""
import os
import pathlib
import shutil
import subprocess
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
LAUNCHER = ROOT / "clients" / "launcher"


class LauncherSafety(unittest.TestCase):
    @unittest.skipUnless(os.name == "nt" and shutil.which("pwsh"), "Windows and PowerShell 7 required for DPAPI")
    def test_config_and_real_windows_dpapi(self):
        result = subprocess.run(
            [shutil.which("pwsh"), "-NoProfile", "-File", str(ROOT / "tests/unit/launcher/run-tests.ps1")],
            capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=60,
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertRegex(result.stdout, r"PASS: \d+ launcher configuration and Windows DPAPI checks")

    def test_all_windows_gate_startup_network_calls_on_config_error(self):
        for name in ("MainWindow", "LoginWindow", "RegisterWindow"):
            source = (LAUNCHER / (name + ".xaml.cs")).read_text(encoding="utf-8-sig")
            startup = source.split("private void Window_Loaded(", 1)[1].split("protected override", 1)[0]
            self.assertLess(startup.index("_configurationError is not null"), startup.index("LoadBackgroundAsync()"))
            self.assertLess(startup.index("return;"), startup.index("LoadBackgroundAsync()"))
            self.assertIn('"服务器配置错误"', startup)

    def test_no_author_endpoint_in_launcher_sources(self):
        for path in [LAUNCHER / "DNFLogin/LoginConfig.cs", *LAUNCHER.glob("*.cs")]:
            self.assertNotIn("dnf.cv58.xyz", path.read_text(encoding="utf-8-sig"))


if __name__ == "__main__":
    unittest.main()
