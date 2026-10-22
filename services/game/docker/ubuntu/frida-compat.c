/* SPDX-License-Identifier: MIT
 * Compatibility for the pinned archived i386 Frida gadget only.
 *
 * glibc commit 7a5db2e82fbb6c3a6e3fdae02b7166c5d0e8c7a8 removed the
 * GLIBC_PRIVATE export __libc_dlopen_mode. The archived gadget has one call
 * site, at 0x16fe13, passing RTLD_LAZY | 0x80000000 (__RTLD_DLOPEN).
 * Public dlopen already supplies the internal loader context; strip that
 * one private bit, preserving every public flag and rejecting unknown ones
 * through dlopen itself. Do not export this globally through ld.so.preload.
 * This is deliberately not a general emulation of glibc private APIs.
 */
#define _GNU_SOURCE
#include <dlfcn.h>

_Static_assert(sizeof(void *) == 4, "this compatibility shim is i386 only");

void *__libc_dlopen_mode(const char *name, int mode) {
    const unsigned int internal_dlopen = 0x80000000U;
    return dlopen(name, (int) ((unsigned int) mode & ~internal_dlopen));
}
