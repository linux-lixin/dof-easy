#define _GNU_SOURCE
#include <dlfcn.h>
#include <link.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* Execute through the real ELF loader, without invoking game-only exports.
 * This executable is run ONLY inside local_environment.py's offline container.
 */
int main(int argc, char **argv) {
    if ((argc != 3 && argc != 4) || (strcmp(argv[2], "original") && strcmp(argv[2], "renamed"))) return 64;
    if (argc == 4 && strcmp(argv[3], "lazy-diagnostic")) return 64;
    setvbuf(stdout, NULL, _IONBF, 0);
    printf("loader-begin word-bits=%zu file=%s\n", sizeof(void *) * 8, argv[1]);
    /* Lazy binding mirrors normal LD_PRELOAD startup, but does NOT prove that
     * every deferred symbol can resolve. Keep the strict NOW result separately. */
    int binding = argc == 4 ? RTLD_LAZY : RTLD_NOW;
    printf("binding=%s\n", binding == RTLD_NOW ? "now" : "lazy-diagnostic");
    void *handle = dlopen(argv[1], binding | RTLD_LOCAL);
    if (!handle) { fprintf(stderr, "dlopen-failed: %s\n", dlerror()); return 2; }
    struct link_map *map = NULL;
    if (dlinfo(handle, RTLD_DI_LINKMAP, &map) || !map) return 3;
    printf("constructors-returned base=%p\n", (void *)map->l_addr);
    void *entry = dlsym(handle, "GetAntiBotInterfaceAll");
    if (entry) {
        const uintptr_t sites[] = {0x6316, 0x8080, 0x85f7, 0x8a8d, 0x8141};
        const char *old[] = {"qk", "/root/config.ini", "/root/config.ini", "<qt><c>qkRobotc</c><key>", "/home/neople/game/privatekey.pem"};
        const char *names[] = {"dof_native", "/data/dof-easy/config/gateway.ini", "/data/dof-easy/config/gateway.ini", "<qt><c>dofRobotc</c><key>", "/data/dof-easy/keys/privatekey.pem"};
        int renamed = !strcmp(argv[2], "renamed");
        if ((uintptr_t)entry - map->l_addr != 0x7ede) return 4;
        for (size_t i = 0; i < sizeof(sites) / sizeof(sites[0]); ++i) {
            int32_t operand;
            memcpy(&operand, (void *)(map->l_addr + sites[i]), sizeof operand);
            const char *value = (const char *)(map->l_addr + 0x45360 + operand);
            const char *expected = renamed ? names[i] : old[i];
            if (strcmp(value, expected)) { fprintf(stderr, "name-reference-mismatch slot=%zu\n", i); return 5; }
            printf("name-reference-ok slot=%zu value=%s\n", i, value);
        }
    }
    FILE *maps = fopen("/proc/self/maps", "r");
    if (!maps) return 6;
    char line[2048];
    while (fgets(line, sizeof line, maps)) {
        if (strstr(line, "/inputs/") || strstr(line, "libstdc++") || strstr(line, "ld-linux")) fputs(line, stdout);
    }
    fclose(maps);
    if (dlclose(handle)) { fprintf(stderr, "dlclose-failed: %s\n", dlerror()); return 7; }
    puts("destructors-returned");
    return 0;
}
