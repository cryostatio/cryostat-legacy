#!/bin/sh

set -x
set -e

function cleanup() {
    podman pod kill cryostat-pod
    podman pod rm cryostat-pod
}
trap cleanup EXIT

if [ -z "$CRYOSTAT_IMAGE" ]; then
    CRYOSTAT_IMAGE="quay.io/cryostat/cryostat:latest"
fi

echo -e "\n\nRunning $CRYOSTAT_IMAGE ...\n\n"

if [ -z "$CRYOSTAT_RJMX_PORT" ]; then
    CRYOSTAT_RJMX_PORT="$(xpath -q -e 'project/properties/cryostat.rjmxPort/text()' pom.xml)"
fi

if [ -z "$CRYOSTAT_RMI_PORT" ]; then
    CRYOSTAT_RMI_PORT="$CRYOSTAT_RJMX_PORT"
fi

if [ -z "$CRYOSTAT_WEB_HOST" ]; then
    CRYOSTAT_WEB_HOST="$(xpath -q -e 'project/properties/cryostat.itest.webHost/text()' pom.xml)"
fi

if [ -z "$CRYOSTAT_WEB_PORT" ]; then
    CRYOSTAT_WEB_PORT="$(xpath -q -e 'project/properties/cryostat.itest.webPort/text()' pom.xml)"
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

if [ ! -d "$(dirname $0)/archive" ]; then
    mkdir "$(dirname $0)/archive"
fi

if [ ! -d "$(dirname $0)/conf" ]; then
    mkdir "$(dirname $0)/conf"
fi

if [ ! -d "$(dirname $0)/conf/credentials" ]; then
    mkdir "$(dirname $0)/conf/credentials"
fi

if [ ! -d "$(dirname $0)/conf/rules" ]; then
    mkdir "$(dirname $0)/conf/rules"
fi

if [ ! -d "$(dirname $0)/truststore" ]; then
    mkdir "$(dirname $0)/truststore"
fi

if [ ! -d "$(dirname $0)/clientlib" ]; then
    mkdir "$(dirname $0)/clientlib"
fi

if [ ! -d "$(dirname $0)/templates" ]; then
    mkdir "$(dirname $0)/templates"
fi

if ! podman pod exists cryostat-pod; then
    podman pod create \
        --hostname cryostat \
        --name cryostat-pod \
        --publish $CRYOSTAT_RJMX_PORT:$CRYOSTAT_RJMX_PORT \
        --publish $CRYOSTAT_EXT_WEB_PORT:$CRYOSTAT_WEB_PORT
fi

# run as root (uid 0) within the container - with rootless podman this means
# that the process will actually run with your own uid on the host machine,
# rather than the uid being remapped to something else
podman run \
    --pod cryostat-pod \
    --name cryostat \
    --user 0 \
    --memory 768M \
    --mount type=bind,source="$(dirname $0)/archive",destination=/opt/cryostat.d/recordings.d,relabel=shared \
    --mount type=bind,source="$(dirname $0)/certs",destination=/certs,relabel=shared \
    --mount type=bind,source="$(dirname $0)/clientlib",destination=/clientlib,relabel=shared \
    --mount type=bind,source="$(dirname $0)/conf",destination=/opt/cryostat.d/conf.d,relabel=shared \
    --mount type=bind,source="$(dirname $0)/templates",destination=/opt/cryostat.d/templates.d,relabel=shared \
    --mount type=bind,source="$(dirname $0)/truststore",destination=/truststore,relabel=shared \
    --mount type=tmpfs,target=/opt/cryostat.d/probes.d \
    -e CRYOSTAT_ENABLE_JDP_BROADCAST=true \
    -e CRYOSTAT_REPORT_GENERATOR=$CRYOSTAT_REPORT_GENERATOR \
    -e CRYOSTAT_PLATFORM=$CRYOSTAT_PLATFORM \
    -e CRYOSTAT_DISABLE_BUILTIN_DISCOVERY=$CRYOSTAT_DISABLE_BUILTIN_DISCOVERY \
    -e CRYOSTAT_DISABLE_SSL=$CRYOSTAT_DISABLE_SSL \
    -e CRYOSTAT_DISABLE_JMX_AUTH=$CRYOSTAT_DISABLE_JMX_AUTH \
    -e CRYOSTAT_ALLOW_UNTRUSTED_SSL=$CRYOSTAT_ALLOW_UNTRUSTED_SSL \
    -e CRYOSTAT_RJMX_USER=$CRYOSTAT_RJMX_USER \
    -e CRYOSTAT_RJMX_PASS=$CRYOSTAT_RJMX_PASS \
    -e CRYOSTAT_RJMX_PORT=$CRYOSTAT_RJMX_PORT \
    -e CRYOSTAT_RMI_PORT=$CRYOSTAT_RMI_PORT \
    -e CRYOSTAT_CORS_ORIGIN=$CRYOSTAT_CORS_ORIGIN \
    -e CRYOSTAT_WEB_HOST=$CRYOSTAT_WEB_HOST \
    -e CRYOSTAT_WEB_PORT=$CRYOSTAT_WEB_PORT \
    -e CRYOSTAT_EXT_WEB_PORT=$CRYOSTAT_EXT_WEB_PORT \
    -e CRYOSTAT_MAX_WS_CONNECTIONS=$CRYOSTAT_MAX_WS_CONNECTIONS \
    -e CRYOSTAT_AUTH_MANAGER=$CRYOSTAT_AUTH_MANAGER \
    -e CRYOSTAT_TARGET_CACHE_SIZE=$CRYOSTAT_TARGET_CACHE_SIZE \
    -e CRYOSTAT_TARGET_CACHE_TTL=$CRYOSTAT_TARGET_CACHE_TTL \
    -e CRYOSTAT_CONFIG_PATH="/opt/cryostat.d/conf.d" \
    -e CRYOSTAT_ARCHIVE_PATH="/opt/cryostat.d/recordings.d" \
    -e CRYOSTAT_TEMPLATE_PATH="/opt/cryostat.d/templates.d" \
    -e CRYOSTAT_PROBE_TEMPLATE_PATH="/opt/cryostat.d/probes.d" \
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
