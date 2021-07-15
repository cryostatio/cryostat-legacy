#!/bin/sh

set -x
set -e

function cleanup() {
    podman pod kill cryostat
    podman pod rm cryostat
}
trap cleanup EXIT

if [ -z "$CRYOSTAT_IMAGE" ]; then
    CRYOSTAT_IMAGE="quay.io/cryostat/cryostat:latest"
fi

echo -e "\n\nRunning $CRYOSTAT_IMAGE ...\n\n"

if [ -z "$CRYOSTAT_RJMX_PORT" ]; then
    CRYOSTAT_RJMX_PORT=9091
fi

if [ -z "$CRYOSTAT_WEB_HOST" ]; then
    CRYOSTAT_WEB_HOST="0.0.0.0" # listens on all interfaces and hostnames for testing purposes
fi

if [ -z "$CRYOSTAT_WEB_PORT" ]; then
    CRYOSTAT_WEB_PORT=8181
fi

if [ -z "$CRYOSTAT_EXT_WEB_PORT" ]; then
    CRYOSTAT_EXT_WEB_PORT="$CRYOSTAT_WEB_PORT"
fi

if [ -z "$CRYOSTAT_AUTH_MANAGER" ]; then
    CRYOSTAT_AUTH_MANAGER="io.cryostat.net.NoopAuthManager"
fi

if [ -z "$CRYOSTAT_REPORT_GENERATION_MAX_HEAP" ]; then
    CRYOSTAT_REPORT_GENERATION_MAX_HEAP="200"
fi

if [ -z "$KEYSTORE_PATH" ] && [ -f "$(dirname $0)/certs/cryostat-keystore.p12" ] ; then
    KEYSTORE_PATH="/certs/cryostat-keystore.p12"
    KEYSTORE_PASS="$(cat $(dirname $0)/certs/keystore.pass)"
fi

if [ ! -d "$(dirname $0)/truststore" ]; then
    mkdir "$(dirname $0)/truststore"
fi

if [ ! -d "$(dirname $0)/clientlib" ]; then
    mkdir "$(dirname $0)/clientlib"
fi

if ! podman pod exists cryostat; then
    podman pod create \
        --hostname cryostat \
        --name cryostat \
        --publish $CRYOSTAT_RJMX_PORT:$CRYOSTAT_RJMX_PORT \
        --publish $CRYOSTAT_EXT_WEB_PORT:$CRYOSTAT_WEB_PORT
fi

podman run \
    --pod cryostat \
    --mount type=tmpfs,target=/opt/cryostat.d/conf.d \
    --mount type=tmpfs,target=/opt/cryostat.d/recordings.d \
    --mount type=tmpfs,target=/opt/cryostat.d/templates.d \
    --mount type=bind,source="$(dirname $0)/truststore",destination=/truststore,relabel=shared,bind-propagation=shared \
    --mount type=bind,source="$(dirname $0)/certs",destination=/certs,relabel=shared,bind-propagation=shared \
    --mount type=bind,source="$(dirname $0)/clientlib",destination=/clientlib,relabel=shared,bind-propagation=shared \
    -e CRYOSTAT_PLATFORM=$CRYOSTAT_PLATFORM \
    -e CRYOSTAT_DISABLE_SSL=$CRYOSTAT_DISABLE_SSL \
    -e CRYOSTAT_DISABLE_JMX_AUTH=$CRYOSTAT_DISABLE_JMX_AUTH \
    -e CRYOSTAT_RJMX_USER=$CRYOSTAT_RJMX_USER \
    -e CRYOSTAT_RJMX_PASS=$CRYOSTAT_RJMX_PASS \
    -e CRYOSTAT_RJMX_PORT=$CRYOSTAT_RJMX_PORT \
    -e CRYOSTAT_CORS_ORIGIN=$CRYOSTAT_CORS_ORIGIN \
    -e CRYOSTAT_WEB_HOST=$CRYOSTAT_WEB_HOST \
    -e CRYOSTAT_WEB_PORT=$CRYOSTAT_WEB_PORT \
    -e CRYOSTAT_EXT_WEB_PORT=$CRYOSTAT_EXT_WEB_PORT \
    -e CRYOSTAT_AUTH_MANAGER=$CRYOSTAT_AUTH_MANAGER \
    -e CRYOSTAT_CONFIG_PATH="/opt/cryostat.d/conf.d" \
    -e CRYOSTAT_ARCHIVE_PATH="/opt/cryostat.d/recordings.d" \
    -e CRYOSTAT_TEMPLATE_PATH="/opt/cryostat.d/templates.d" \
    -e CRYOSTAT_CLIENTLIB_PATH="/clientlib" \
    -e CRYOSTAT_REPORT_GENERATION_MAX_HEAP="$CRYOSTAT_REPORT_GENERATION_MAX_HEAP" \
    -e GRAFANA_DATASOURCE_URL=$GRAFANA_DATASOURCE_URL \
    -e GRAFANA_DASHBOARD_URL=$GRAFANA_DASHBOARD_URL \
    -e KEYSTORE_PATH=$KEYSTORE_PATH \
    -e KEYSTORE_PASS=$KEYSTORE_PASS \
    -e KEY_PATH=$KEY_PATH \
    -e CERT_PATH=$CERT_PATH \
    -e CRYOSTAT_JUL_CONFIG=$CRYOSTAT_JUL_CONFIG \
    --rm -it "$CRYOSTAT_IMAGE" "$@"
