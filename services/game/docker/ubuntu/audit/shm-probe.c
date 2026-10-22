/* SPDX-License-Identifier: MIT */
#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <unistd.h>

static void check(int ok, const char *message) {
    if (!ok) { perror(message); exit(1); }
}
static void exercise(const char *name, const char *path) {
    shm_unlink(name);
    int fd = shm_open(name, O_CREAT | O_EXCL | O_RDWR, 0600);
    check(fd >= 0, name);
    check(fcntl(fd, F_GETFD) & FD_CLOEXEC, "CLOEXEC");
    check(ftruncate(fd, 4096) == 0, "truncate");
    char *map = mmap(NULL, 4096, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
    check(map != MAP_FAILED, "mmap");
    strcpy(map, "cross-process shared data");
    pid_t child = fork();
    check(child >= 0, "fork");
    if (child == 0) {
        int other = shm_open(name, O_RDWR, 0);
        if (other < 0) _exit(2);
        char buffer[64] = {0};
        if (read(other, buffer, sizeof(buffer)) < 0 || strcmp(buffer, map)) _exit(3);
        close(other);
        _exit(0);
    }
    int status;
    check(waitpid(child, &status, 0) == child && WIFEXITED(status) && WEXITSTATUS(status) == 0,
          "child shared read");
    struct stat info;
    check(stat(path, &info) == 0, "preserved shared path");
    munmap(map, 4096);
    close(fd);
    check(shm_unlink(name) == 0 && access(path, F_OK) < 0, "unlink");
}
int main(void) {
    mkdir("/dev/shm/sec", 0700);
    exercise("/dnf_native_probe", "/dev/shm/dnf_native_probe");
    exercise("sec/tss_sdk_bus_997", "/dev/shm/sec/tss_sdk_bus_997");
    exercise("/sec/tss_sdk_bus_998", "/dev/shm/sec/tss_sdk_bus_998");
    errno = 0;
    check(shm_open("sec/tss_sdk_bus_../../escape", O_CREAT | O_RDWR, 0600) == -1 && errno == EINVAL,
          "reject traversal");
    check(symlink("/tmp/dnf-shm-must-not-create", "/dev/shm/sec/tss_sdk_bus_999") == 0, "symlink fixture");
    check(shm_open("sec/tss_sdk_bus_999", O_CREAT | O_RDWR, 0600) == -1 && errno == ELOOP,
          "reject symlink");
    unlink("/dev/shm/sec/tss_sdk_bus_999");
    puts("PASS: i386 legacy and native shm create/open/shared-read/unlink, CLOEXEC, traversal and symlink rejection");
    return 0;
}
