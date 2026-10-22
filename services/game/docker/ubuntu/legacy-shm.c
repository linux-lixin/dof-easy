/* SPDX-License-Identifier: MIT
 * Restore only the observed legacy TSS shared-memory name convention.
 * Ubuntu's native shm_open validates POSIX names and rejects embedded '/'.
 */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <stddef.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

static const char *legacy_basename(const char *name) {
    if (!name) return NULL;
    if (*name == '/') ++name;
    const char prefix[] = "sec/tss_sdk_bus_";
    if (strncmp(name, prefix, sizeof(prefix) - 1)) return NULL;
    const char *number = name + sizeof(prefix) - 1;
    if (!*number) return NULL;
    for (const char *p = number; *p; ++p)
        if (*p < '0' || *p > '9') return NULL;
    return name + 4; /* Exact basename beneath /dev/shm/sec; never contains '/'. */
}

static int directory_fd(void) {
    return open("/dev/shm/sec", O_RDONLY | O_DIRECTORY | O_CLOEXEC | O_NOFOLLOW);
}

int shm_open(const char *name, int flags, mode_t mode) {
    const char *base = legacy_basename(name);
    if (!base) {
        int (*native)(const char *, int, mode_t) = dlsym(RTLD_NEXT, "shm_open");
        if (!native) { errno = ENOSYS; return -1; }
        return native(name, flags, mode);
    }
    int directory = directory_fd();
    if (directory < 0) return -1;
    int fd = openat(directory, base, flags | O_CLOEXEC | O_NOFOLLOW, mode);
    int saved_errno = errno;
    close(directory);
    errno = saved_errno;
    return fd;
}

int shm_unlink(const char *name) {
    const char *base = legacy_basename(name);
    if (!base) {
        int (*native)(const char *) = dlsym(RTLD_NEXT, "shm_unlink");
        if (!native) { errno = ENOSYS; return -1; }
        return native(name);
    }
    int directory = directory_fd();
    if (directory < 0) return -1;
    int result = unlinkat(directory, base, 0);
    int saved_errno = errno;
    close(directory);
    errno = saved_errno;
    return result;
}
