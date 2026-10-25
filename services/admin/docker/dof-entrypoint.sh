#!/bin/sh
set -eu

# Dedicated development runtime: never runs init.sql or generates game keys.
[ "${QINGKONG_ADMIN_ENABLED:-false}" = true ] || { echo 'Dof adapter must be explicitly enabled' >&2; exit 1; }
[ "${QINGKONG_ENVIRONMENT:-}" = isolated ] || { echo 'Only isolated instances are supported' >&2; exit 1; }
[ "${QINGKONG_ADAPTER_VERSION:-}" = qingkong-2.8-current-20261024 ] || { echo 'Unsupported adapter version' >&2; exit 1; }
: "${QINGKONG_INSTANCE_ID:?Missing instance identity}"
: "${QINGKONG_DATABASE_UUID:?Missing database identity}"
: "${SPRING_DATASOURCE_URL:?Missing dedicated database connection}"
: "${SPRING_DATASOURCE_USERNAME:?Missing dedicated database account}"
: "${SPRING_DATASOURCE_PASSWORD:?Missing database password}"
[ -f /app/data/qingkong-isolated.json ] || { echo 'Missing isolated deployment marker' >&2; exit 1; }

export NGINX_PORT=${NGINX_PORT:-80} SERVER_PORT=${SERVER_PORT:-9001}
envsubst '${NGINX_PORT} ${SERVER_PORT}' < /etc/nginx/nginx.conf.template > /etc/nginx/nginx.conf
nginx -t
service nginx start
exec java -Xmx160m -XX:MaxMetaspaceSize=128m -XX:ActiveProcessorCount=1 -jar /app/app.jar
