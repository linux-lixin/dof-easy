#!/bin/sh
set -eu

DNF_MAX_LEVEL=${DNF_MAX_LEVEL:-70}
case "$DNF_MAX_LEVEL" in
    55|60|65|70|75|80|85|86|90|95|100|110|120) ;;
    *) echo "DNF_MAX_LEVEL must match a supported deployment level." >&2; exit 1;;
esac
export DNF_MAX_LEVEL

mkdir -p /app/data
if [ ! -f /app/data/private.key ] || [ ! -f /app/data/publickey.pem ]; then
    if [ -f /app/data/private.key ] || [ -f /app/data/publickey.pem ]; then
        echo "Incomplete RSA key pair; restore the matching pair before starting." >&2
        exit 1
    fi
    echo "Generating RSA key pair (2048bit PKCS#8)..."
    openssl genpkey -algorithm RSA -out /app/data/private.key -pkeyopt rsa_keygen_bits:2048
    chmod 600 /app/data/private.key
    openssl rsa -pubout -in /app/data/private.key -out /app/data/publickey.pem
else
    echo "Keys found in /app/data."
fi

MARKER_FILE=/app/data/.db_initialized
if [ ! -f "$MARKER_FILE" ]; then
    # Optional for existing/manual installations; deployment supplies both secrets.
    # Validate before any schema changes. Never print their values.
    if [ -n "${ADMIN_PASSWORD:-}${ADMIN_TEMP_SECRET:-}" ]; then
        : "${ADMIN_PASSWORD:?Set both ADMIN_PASSWORD and ADMIN_TEMP_SECRET}"
        : "${ADMIN_TEMP_SECRET:?Set both ADMIN_PASSWORD and ADMIN_TEMP_SECRET}"
        if [ "${#ADMIN_PASSWORD}" -lt 12 ] || [ "${#ADMIN_PASSWORD}" -gt 128 ] ||
           [ "${#ADMIN_TEMP_SECRET}" -lt 16 ] || [ "${#ADMIN_TEMP_SECRET}" -gt 64 ]; then
            echo "Admin password must be 12-128 characters; temporary secret must be 16-64 characters." >&2
            exit 1
        fi
        case "$ADMIN_TEMP_SECRET" in *[!a-zA-Z0-9_-]*)
            echo "ADMIN_TEMP_SECRET must use ASCII letters, digits, underscore or hyphen." >&2; exit 1;; esac
    fi
    DNF_PUBLIC_IP=${DNF_PUBLIC_IP:-127.0.0.1}
    case "$DNF_PUBLIC_IP" in ''|*[!a-zA-Z0-9.:-]*)
        echo "DNF_PUBLIC_IP must be a host name or IP address without a scheme or path." >&2; exit 1;; esac
    export DNF_PUBLIC_IP
    : "${SPRING_DATASOURCE_URL:?Set SPRING_DATASOURCE_URL}"
    : "${SPRING_DATASOURCE_USERNAME:?Set SPRING_DATASOURCE_USERNAME}"
    case "$SPRING_DATASOURCE_URL" in
        jdbc:mysql://*) ;;
        *) echo "Expected jdbc:mysql://host:port/database URL" >&2; exit 1 ;;
    esac
    PROTO_REMOVED=${SPRING_DATASOURCE_URL#jdbc:mysql://}
    DB_HOST=$(printf '%s\n' "$PROTO_REMOVED" | sed -e 's|:.*||' -e 's|/.*||' -e 's|?.*||')
    DB_PORT=$(printf '%s\n' "$PROTO_REMOVED" | sed -n 's|.*:\([0-9]*\).*|\1|p')
    DB_PORT=${DB_PORT:-3306}
    DB_NAME=$(printf '%s\n' "$PROTO_REMOVED" | cut -d/ -f2- | cut -d? -f1)
    case "$DB_NAME" in ''|*[!a-zA-Z0-9_]*) echo "Invalid database name" >&2; exit 1 ;; esac
    export DB_NAME

    # The CLI has its own TLS settings; JDBC useSSL=false does not affect it.
    # Runtime image installs MariaDB client. Only opt out explicitly for legacy DBs.
    set --
    case "${MYSQL_INIT_SSL_MODE:-auto}" in
        auto) ;;
        disabled) set -- --skip-ssl ;;
        *) echo "MYSQL_INIT_SSL_MODE must be auto or disabled" >&2; exit 1 ;;
    esac
    db_query() {
        MYSQL_PWD=${SPRING_DATASOURCE_PASSWORD:-} mysql "$@" \
            --default-character-set=utf8 -h "$DB_HOST" -P "$DB_PORT" -u "$SPRING_DATASOURCE_USERNAME"
    }
    echo "Checking first initialization at $DB_HOST:$DB_PORT, database $DB_NAME..."
    # init.sql contains DROP TABLE and ADD COLUMN. Never replay it on a partial DB.
    existing=$(db_query "$@" -N -e "SELECT
        (SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='$DB_NAME') +
        (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='d_taiwan'
         AND TABLE_NAME='accounts' AND COLUMN_NAME IN ('admin','parent_uid','isRobot'));")
    if [ "$existing" != 0 ]; then
        echo "Existing/partial backend schema without initialization marker; inspect and complete it manually. No SQL imported." >&2
        exit 1
    fi
    envsubst '${DB_NAME} ${DNF_PUBLIC_IP} ${DNF_MAX_LEVEL}' < /app/init.sql.template > /app/init.sql
    if ! db_query "$@" < /app/init.sql; then
        echo "Database initialization failed; stopping before Nginx/Java. No success marker written." >&2
        exit 1
    fi
    if [ -n "${ADMIN_PASSWORD:-}" ]; then
        # Hex encodings avoid SQL quoting/injection and keep plaintext out of files/argv.
        ADMIN_HASH=$(printf '%s' "$ADMIN_PASSWORD" | openssl dgst -md5 | awk '{print $NF}')
        TEMP_HEX=$(printf '%s' "$ADMIN_TEMP_SECRET" | od -An -tx1 | tr -d ' \n')
        if ! db_query "$@" <<SQL
INSERT INTO d_taiwan.accounts (accountname, password, qq, VIP, admin, parent_uid, isRobot)
SELECT 'admin', '$ADMIN_HASH', '', '', 1, 0, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM d_taiwan.accounts WHERE accountname='admin' OR admin=1);
INSERT INTO \`$DB_NAME\`.ADMIN_TEMP_PASSWORD (id, PASSWORD)
SELECT 1, CONVERT(0x$TEMP_HEX USING latin1)
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM \`$DB_NAME\`.ADMIN_TEMP_PASSWORD);
SQL
        then
            echo "Admin bootstrap failed; inspect the partial schema. No success marker written." >&2
            exit 1
        fi
        unset ADMIN_HASH TEMP_HEX
    fi
    touch "$MARKER_FILE"
    echo "Database initialized successfully."
else
    echo "DB already initialized."
fi

# These initialization-only values must not be inherited by Nginx or Java.
unset ADMIN_PASSWORD ADMIN_TEMP_SECRET

envsubst '${NGINX_PORT} ${SERVER_PORT}' < /etc/nginx/nginx.conf.template > /etc/nginx/nginx.conf
service nginx start
exec java -jar /app/app.jar
