#!/bin/sh
set -eu

# Dedicated development runtime: never runs init.sql or generates game keys.
case "${LAUNCHER_MODE:-}" in
  native|dof) ;;
  *) echo 'Unsupported launcher mode' >&2; exit 1 ;;
esac
if env | cut -d= -f1 | grep -q '^QINGKONG_'; then
  echo 'Legacy native environment is not supported' >&2; exit 1
fi
[ "${DOF_ADMIN_ENABLED:-false}" = true ] || { echo 'Dof adapter must be explicitly enabled' >&2; exit 1; }
[ "${DOF_ENVIRONMENT:-}" = isolated ] || { echo 'Only isolated instances are supported' >&2; exit 1; }
[ "${DOF_ADAPTER_VERSION:-}" = dof-native-1 ] || { echo 'Unsupported adapter version' >&2; exit 1; }
: "${DOF_INSTANCE_ID:?Missing instance identity}"
: "${DOF_DATABASE_UUID:?Missing database identity}"
: "${SPRING_DATASOURCE_URL:?Missing dedicated database connection}"
: "${SPRING_DATASOURCE_USERNAME:?Missing dedicated database account}"
: "${SPRING_DATASOURCE_PASSWORD:?Missing database password}"
[ -f /app/data/dof-isolated.json ] || { echo 'Missing isolated deployment marker' >&2; exit 1; }

export NGINX_PORT=${NGINX_PORT:-80} SERVER_PORT=${SERVER_PORT:-9001}
# Source-built images retain both proxy templates; prebuilt images already
# install the native template at the generic path.
if [ -f /etc/nginx/dof-nginx.conf.template ]; then
  cp /etc/nginx/dof-nginx.conf.template /etc/nginx/nginx.conf.template
fi
envsubst '${NGINX_PORT} ${SERVER_PORT}' < /etc/nginx/nginx.conf.template > /etc/nginx/nginx.conf
nginx -t
service nginx start
exec java -Xmx160m -XX:MaxMetaspaceSize=128m -XX:ActiveProcessorCount=1 -jar /app/app.jar
