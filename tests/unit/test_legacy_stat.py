"""Real i386 ABI tests, including Windows bind-mount directory inode overflow."""
import errno
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
PROBE = r'''
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/stat.h>
#include <unistd.h>
extern int __xstat(int, const char *, struct stat *);
extern int __lxstat(int, const char *, struct stat *);
extern int __fxstat(int, int, struct stat *);
int main(int argc, char **argv) {
    if (argc != 3) return 99;
    struct stat result;
    int code;
    int kind = atoi(argv[2]);
    if (kind == 0) code = __xstat(3, argv[1], &result);
    else if (kind == 1) code = __lxstat(3, argv[1], &result);
    else {
        int fd = open(argv[1], O_RDONLY);
        if (fd < 0) return errno;
        code = __fxstat(3, fd, &result);
        int saved = errno;
        close(fd);
        errno = saved;
    }
    if (code) return errno;
    printf("%o\n", result.st_mode & S_IFMT);
    return 0;
}
'''


@unittest.skipUnless(sys.platform == 'linux' and shutil.which('gcc'), 'requires Linux gcc with i386 support')
class LegacyStat(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory(dir=ROOT)
        cls.root = Path(cls.temp.name)
        cls.probe = cls.root / 'probe'
        cls.shim = cls.root / 'stat.so'
        (cls.root / 'probe.c').write_text(PROBE)
        for args in ([str(cls.root / 'probe.c'), '-o', str(cls.probe)],
                     ['-shared', '-fPIC', str(ROOT / 'services/game/docker/ubuntu/legacy-stat.c'), '-ldl', '-o', str(cls.shim)]):
            result = subprocess.run(['gcc', '-m32', '-Wall', '-Wextra', '-Werror', *args], capture_output=True, text=True)
            if result.returncode:
                cls.temp.cleanup()
                raise AssertionError(result.stderr)

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def probe_path(self, path, kind, preload):
        env = dict(os.environ)
        env.pop('LD_PRELOAD', None)
        if preload:
            env['LD_PRELOAD'] = str(self.shim)
        return subprocess.run([str(self.probe), str(path), str(kind)], env=env, capture_output=True, text=True)

    def test_directory_stat_lstat_and_fstat_work_on_large_inodes(self):
        directory = self.root / 'directory'
        directory.mkdir(exist_ok=True)
        for kind in range(3):
            native = self.probe_path(directory, kind, False)
            self.assertIn(native.returncode, (0, errno.EOVERFLOW), native.stderr)
            fixed = self.probe_path(directory, kind, True)
            self.assertEqual(fixed.returncode, 0, fixed.stderr)
            self.assertEqual(fixed.stdout.strip(), '40000')

    def test_regular_files_and_missing_paths_keep_original_results(self):
        regular = self.root / 'file'
        regular.write_text('unchanged contents')
        for path in (regular, self.root / 'absent'):
            for kind in range(3):
                native = self.probe_path(path, kind, False)
                fixed = self.probe_path(path, kind, True)
                self.assertEqual((fixed.returncode, fixed.stdout), (native.returncode, native.stdout))
        self.assertEqual(regular.read_text(), 'unchanged contents')


if __name__ == '__main__':
    unittest.main()
