#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

int main(int argc, char **argv) {
    const char *names[] = {"DNF_DB_GAME_PASSWORD", "DEC_GAME_PWD",
        "DNF_DB_ROOT_PASSWORD", "MAIN_MYSQL_ROOT_PASSWORD", "WEB_PASS",
        "GM_PASSWORD", "GM_CONNECT_KEY", NULL};
    printf("native-pid=%ld\n", (long)getpid());
    fflush(stdout);
    if (argc > 1 && strcmp(argv[1], "wait") == 0) {
        while (1) pause();
    }
    for (int fd = 1; fd <= 2; ++fd) {
        for (int i = 0; names[i]; ++i) {
            const char *value = getenv(names[i]);
            dprintf(fd, "%s=", names[i]);
            /* Cross multiple read boundaries, including the reader's idle period. */
            for (size_t j = 0; j < strlen(value); ++j) {
                write(fd, value + j, 1);
                usleep(2000);
            }
            dprintf(fd, "\n");
        }
        dprintf(fd, "DB host=local pw='unknown_unconfigured_secret' done\n");
        dprintf(fd, "DB PW = unquoted_unknown_secret\n");
        dprintf(fd, "Game DB Password: unknown_game_encoded_secret\n");
        dprintf(fd, "Auction DB Password: unknown_auction_encoded_secret\n");
        for (int i = 0; i < 70000; ++i) write(fd, "x", 1);
        dprintf(fd, "overflow_unknown pw='overflow_secret'\n");
        dprintf(fd, "stream-%d-readable\n", fd);
        dprintf(fd, "path=/opt/dnf/example/data\n");
        dprintf(fd, "EOF:%s", getenv("WEB_PASS"));
    }
    return 37;
}
