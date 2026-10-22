/* Exercise the same default database initialization used by df_game_r. */
#include <stdio.h>
#include <GeoIP.h>

int main(void) {
    GeoIP *database = GeoIP_new(GEOIP_STANDARD);
    if (database == NULL) {
        fputs("FAIL: GeoIP_new could not initialize the default database\n", stderr);
        return 1;
    }
    int edition = GeoIP_database_edition(database);
    GeoIP_delete(database);
    if (edition != GEOIP_COUNTRY_EDITION) {
        fprintf(stderr, "FAIL: expected country database, got edition %d\n", edition);
        return 1;
    }
    puts("PASS: i386 GeoIP_new loaded the default country database");
    return 0;
}
