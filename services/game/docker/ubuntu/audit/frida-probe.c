/* SPDX-License-Identifier: MIT
 * A harmless i386 host for the archived gadget; never loads game code. */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <stdio.h>
#include <string.h>

__attribute__((noinline, visibility("default")))
int vipai_probe_target(int value) { return value + 1; }

int main(int argc, char **argv) {
    if (argc != 2) return 2;
    if (strcmp(argv[1], "--shim") == 0) {
        void *(*legacy_open)(const char *, int) =
            (void *(*)(const char *, int)) dlvsym(RTLD_DEFAULT, "__libc_dlopen_mode", "GLIBC_PRIVATE");
        if (!legacy_open) { fprintf(stderr, "%s\n", dlerror()); return 3; }
        void *handle = legacy_open("libm.so.6", (int) 0x80000001U);
        if (!handle || !dlsym(handle, "cos")) return 4;
        dlclose(handle);
        /* Unknown flags must not be silently discarded. */
        if (legacy_open("libm.so.6", (int) 0x80800001U) != NULL) return 5;
        puts("PASS: versioned private flag translation and invalid-flag rejection");
        return 0;
    }
    void *gadget = dlopen(argv[1], RTLD_NOW | RTLD_GLOBAL);
    if (!gadget) { fprintf(stderr, "%s\n", dlerror()); return 6; }
    int answer = vipai_probe_target(40);
    if (answer != 42) { fprintf(stderr, "hook returned %d instead of 42\n", answer); return 7; }
    puts("PASS: archived i386 gadget loaded and JavaScript replaced a native function");
    /* Gadget is intentionally process-lifetime; do not force unsafe unloading. */
    return 0;
}
