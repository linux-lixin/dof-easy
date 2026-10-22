/* SPDX-License-Identifier: MIT
 * Old i386 binaries use the version-3 stat ABI with a 32-bit inode field.
 * Docker Desktop bind mounts can expose directory inode numbers above 2^32.
 * Retry ONLY inode-overflowing directory queries; preserve all other errors,
 * file metadata, permissions, timestamps and size-overflow behaviour.
 */
#define _GNU_SOURCE
#define _LARGEFILE64_SOURCE
#include <dlfcn.h>
#include <errno.h>
#include <limits.h>
#include <stdint.h>
#include <string.h>
#include <sys/stat.h>

#if !defined(__i386__)
#error This adapter is only for the legacy i386 stat ABI.
#endif

static int directory_stat(const struct stat64 *wide, struct stat *narrow) {
    if (!S_ISDIR(wide->st_mode) || wide->st_ino <= UINT32_MAX ||
        wide->st_size > INT32_MAX || wide->st_size < 0 ||
        wide->st_blocks > INT32_MAX || wide->st_blocks < 0 ||
        wide->st_atim.tv_sec > INT32_MAX || wide->st_atim.tv_sec < INT32_MIN ||
        wide->st_mtim.tv_sec > INT32_MAX || wide->st_mtim.tv_sec < INT32_MIN ||
        wide->st_ctim.tv_sec > INT32_MAX || wide->st_ctim.tv_sec < INT32_MIN) {
        errno = EOVERFLOW;
        return -1;
    }
    memset(narrow, 0, sizeof(*narrow));
    narrow->st_dev = wide->st_dev;
    narrow->st_ino = (ino_t)(wide->st_ino & UINT32_MAX);
    narrow->st_mode = wide->st_mode;
    narrow->st_nlink = wide->st_nlink;
    narrow->st_uid = wide->st_uid;
    narrow->st_gid = wide->st_gid;
    narrow->st_rdev = wide->st_rdev;
    narrow->st_size = wide->st_size;
    narrow->st_blksize = wide->st_blksize;
    narrow->st_blocks = wide->st_blocks;
    narrow->st_atim = wide->st_atim;
    narrow->st_mtim = wide->st_mtim;
    narrow->st_ctim = wide->st_ctim;
    errno = 0;
    return 0;
}

#define PATH_STAT(symbol, wide_call) \
int symbol(int version, const char *path, struct stat *buffer) { \
    int (*native)(int, const char *, struct stat *) = dlsym(RTLD_NEXT, #symbol); \
    if (!native) { errno = ENOSYS; return -1; } \
    int result = native(version, path, buffer); \
    if (result == 0 || errno != EOVERFLOW || version != 3) return result; \
    struct stat64 wide; \
    if (wide_call(path, &wide)) return -1; \
    return directory_stat(&wide, buffer); \
}

PATH_STAT(__xstat, stat64)
PATH_STAT(__lxstat, lstat64)

int __fxstat(int version, int fd, struct stat *buffer) {
    int (*native)(int, int, struct stat *) = dlsym(RTLD_NEXT, "__fxstat");
    if (!native) { errno = ENOSYS; return -1; }
    int result = native(version, fd, buffer);
    if (result == 0 || errno != EOVERFLOW || version != 3) return result;
    struct stat64 wide;
    if (fstat64(fd, &wide)) return -1;
    return directory_stat(&wide, buffer);
}
