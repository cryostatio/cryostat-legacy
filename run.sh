#!/bin/sh

set -x
set -e

function cleanup() {
    podman pod kill container-jfr
    podman pod rm container-jfr
}
trap cleanup EXIT

if [ -z "$CONTAINER_JFR_IMAGE" ]; then
    CONTAINER_JFR_IMAGE="quay.io/rh-jmc-team/container-jfr:latest"
fi

echo -e "\n\nRunning $CONTAINER_JFR_IMAGE ...\n\n"

if [ -z "$CONTAINER_JFR_RJMX_PORT" ]; then
    CONTAINER_JFR_RJMX_PORT=9091
fi

if [ -z "$CONTAINER_JFR_WEB_HOST" ]; then
    CONTAINER_JFR_WEB_HOST="0.0.0.0" # listens on all interfaces and hostnames for testing purposes
fi

if [ -z "$CONTAINER_JFR_WEB_PORT" ]; then
    CONTAINER_JFR_WEB_PORT=8181
fi

if [ -z "$CONTAINER_JFR_EXT_WEB_PORT" ]; then
    CONTAINER_JFR_EXT_WEB_PORT="$CONTAINER_JFR_WEB_PORT"
fi

if [ -z "$CONTAINER_JFR_LISTEN_HOST" ]; then
    CONTAINER_JFR_LISTEN_HOST="$CONTAINER_JFR_WEB_HOST"
fi

if [ -z "$CONTAINER_JFR_LISTEN_PORT" ]; then
    CONTAINER_JFR_LISTEN_PORT=9090;
fi

if [ -z "$CONTAINER_JFR_EXT_LISTEN_PORT" ]; then
    CONTAINER_JFR_EXT_LISTEN_PORT="$CONTAINER_JFR_LISTEN_PORT"
fi

if [ -z "$CONTAINER_JFR_AUTH_MANAGER" ]; then
    CONTAINER_JFR_AUTH_MANAGER="com.redhat.rhjmc.containerjfr.net.NoopAuthManager"
fi

if [ -z "$CONTAINER_JFR_REPORT_GENERATION_MAX_HEAP" ]; then
    CONTAINER_JFR_REPORT_GENERATION_MAX_HEAP="200"
fi

if [ -z "$KEYSTORE_PATH" ]; then
    KEYSTORE_PATH="/certs/container-jfr-keystore.p12"
    KEYSTORE_PASS="$(cat $(dirname $0)/certs/keystore.pass)"
fi

if [ ! -d "$(dirname $0)/truststore" ]; then
    mkdir "$(dirname $0)/truststore"
fi

if ! podman pod exists container-jfr; then
    podman pod create \
        --hostname container-jfr \
        --name container-jfr \
        --publish $CONTAINER_JFR_RJMX_PORT:$CONTAINER_JFR_RJMX_PORT \
        --publish $CONTAINER_JFR_EXT_LISTEN_PORT:$CONTAINER_JFR_LISTEN_PORT \
        --publish $CONTAINER_JFR_EXT_WEB_PORT:$CONTAINER_JFR_WEB_PORT
fi

podman run \
    --pod container-jfr \
    --mount type=tmpfs,target=/flightrecordings \
    --mount type=tmpfs,target=/templates \
    --mount type=bind,source="$(dirname $0)/truststore",destination=/truststore,relabel=shared,bind-propagation=shared \
    --mount type=bind,source="$(dirname $0)/certs",destination=/certs,relabel=shared,bind-propagation=shared \
    -e CONTAINER_JFR_DISABLE_SSL=$CONTAINER_JFR_DISABLE_SSL \
    -e CONTAINER_JFR_DISABLE_JMX_AUTH=$CONTAINER_JFR_DISABLE_JMX_AUTH \
    -e CONTAINER_JFR_RJMX_USER=$CONTAINER_JFR_RJMX_USER \
    -e CONTAINER_JFR_RJMX_PASS=$CONTAINER_JFR_RJMX_PASS \
    -e CONTAINER_JFR_RJMX_PORT=$CONTAINER_JFR_RJMX_PORT \
    -e CONTAINER_JFR_CORS_ORIGIN=$CONTAINER_JFR_CORS_ORIGIN \
    -e CONTAINER_JFR_WEB_HOST=$CONTAINER_JFR_WEB_HOST \
    -e CONTAINER_JFR_WEB_PORT=$CONTAINER_JFR_WEB_PORT \
    -e CONTAINER_JFR_EXT_WEB_PORT=$CONTAINER_JFR_EXT_WEB_PORT \
    -e CONTAINER_JFR_LISTEN_HOST=$CONTAINER_JFR_LISTEN_HOST \
    -e CONTAINER_JFR_LISTEN_PORT=$CONTAINER_JFR_LISTEN_PORT \
    -e CONTAINER_JFR_EXT_LISTEN_PORT=$CONTAINER_JFR_EXT_LISTEN_PORT \
    -e CONTAINER_JFR_AUTH_MANAGER=$CONTAINER_JFR_AUTH_MANAGER \
    -e CONTAINER_JFR_ARCHIVE_PATH="/flightrecordings" \
    -e CONTAINER_JFR_TEMPLATE_PATH="/templates" \
    -e CONTAINER_JFR_REPORT_GENERATION_MAX_HEAP="$CONTAINER_JFR_REPORT_GENERATION_MAX_HEAP" \
    -e GRAFANA_DATASOURCE_URL=$GRAFANA_DATASOURCE_URL \
    -e GRAFANA_DASHBOARD_URL=$GRAFANA_DASHBOARD_URL \
    -e KEYSTORE_PATH=$KEYSTORE_PATH \
    -e KEYSTORE_PASS=$KEYSTORE_PASS \
    -e KEY_PATH=$KEY_PATH \
    -e CERT_PATH=$CERT_PATH \
    -e CONTAINER_JFR_JUL_CONFIG=$CONTAINER_JFR_JUL_CONFIG \
    --rm -it "$CONTAINER_JFR_IMAGE" "$@"
