#!/bin/sh

set -x
set -e

if [ -z "${MVN}" ]; then
    MVN="$(which mvn)"
fi

getPomProperty() {
    if command -v xpath > /dev/null 2>&1 ; then
        xpath -q -e "project/properties/$1/text()" pom.xml
    else
        "${MVN}" help:help > /dev/null 2>&1
        "${MVN}" build-helper:regex-property@image-tag-to-lower help:evaluate -o -B -q -DforceStdout -Dexpression="$1"
    fi
}

cleanup() {
    podman pod stop cryostat-pod
    podman pod rm cryostat-pod
}
trap cleanup EXIT

if [ -z "$CRYOSTAT_IMAGE" ]; then
    CRYOSTAT_IMAGE="quay.io/cryostat/cryostat:$(${MVN} validate help:evaluate -o -B -q -DforceStdout -Dexpression=cryostat.imageVersionLower)"
fi

printf "\n\nRunning %s ...\n\n", "$CRYOSTAT_IMAGE"

if [ -z "$CRYOSTAT_RJMX_PORT" ]; then
    CRYOSTAT_RJMX_PORT="$(getPomProperty cryostat.rjmxPort)"
fi

if [ -z "$CRYOSTAT_RMI_PORT" ]; then
    CRYOSTAT_RMI_PORT="$CRYOSTAT_RJMX_PORT"
fi

if [ -z "$CRYOSTAT_WEB_HOST" ]; then
    CRYOSTAT_WEB_HOST="$(getPomProperty cryostat.itest.webHost)"
fi

if [ -z "$CRYOSTAT_WEB_PORT" ]; then
    CRYOSTAT_WEB_PORT="$(getPomProperty cryostat.itest.webPort)"
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

if [ -z "$KEYSTORE_PATH" ] && [ -f "$(dirname "$0")/certs/cryostat-keystore.p12" ] ; then
    KEYSTORE_PATH="/certs/cryostat-keystore.p12"
    KEYSTORE_PASS="$(cat "$(dirname "$0")"/certs/keystore.pass)"
fi

if [ ! -d "$(dirname "$0")/archive" ]; then
    mkdir "$(dirname "$0")/archive"
fi

if [ -z "$CRYOSTAT_JMX_CREDENTIALS_DB_PASSWORD" ]; then
    CRYOSTAT_JMX_CREDENTIALS_DB_PASSWORD="$(< /dev/urandom tr -dc _A-Z-a-z-0-9 | head -c32)"
fi

if [ ! -d "$(dirname "$0")/conf" ]; then
    mkdir "$(dirname "$0")/conf"
fi

if [ ! -d "$(dirname "$0")/conf/credentials" ]; then
    mkdir "$(dirname "$0")/conf/credentials"
fi

if [ ! -d "$(dirname "$0")/conf/rules" ]; then
    mkdir "$(dirname "$0")/conf/rules"
fi

if [ ! -d "$(dirname "$0")/truststore" ]; then
    mkdir "$(dirname "$0")/truststore"
fi

if [ ! -d "$(dirname "$0")/clientlib" ]; then
    mkdir "$(dirname "$0")/clientlib"
fi

if [ ! -d "$(dirname "$0")/templates" ]; then
    mkdir "$(dirname "$0")/templates"
fi

if [ ! -d "$(dirname "$0")/probes" ]; then
    mkdir "$(dirname "$0")/probes"
fi

if ! podman pod exists cryostat-pod; then
    podman pod create \
        --hostname cryostat \
        --name cryostat-pod \
        --publish "$CRYOSTAT_RJMX_PORT":"$CRYOSTAT_RJMX_PORT" \
        --publish "$CRYOSTAT_EXT_WEB_PORT":"$CRYOSTAT_WEB_PORT"
fi

# do: $ podman system service -t 0
# or do: $ systemctl --user start podman.socket
# to create the podman.sock to volume-mount into the container
#
# to check the podman socket is reachable and connectable within the container:
# $ podman exec -it cryo /bin/sh
# sh-4.4# curl -v -s --unix-socket /run/user/0/podman/podman.sock http://d:80/v3.0.0/libpod/info
#
# run as root (uid 0) within the container - with rootless podman this means
# that the process will actually run with your own uid on the host machine,
# rather than the uid being remapped to something else
#
# limits set to match operator defaults:
# https://github.com/cryostatio/cryostat-operator/blob/2d386930dc96f0dcaf937987ec35874006c53b61/internal/controllers/common/resource_definitions/resource_definitions.go#L66
podman run \
    --pod cryostat-pod \
    --name cryostat \
    --user 0 \
    --health-cmd 'curl -k --fail "http://localhost:${CRYOSTAT_WEB_PORT}/health/liveness" || curl -k --fail "https://localhost:${CRYOSTAT_WEB_PORT}/health/liveness"' \
    --label io.cryostat.discovery="true" \
    --label io.cryostat.jmxHost="localhost" \
    --label io.cryostat.jmxPort="0" \
    --label io.cryostat.jmxUrl="service:jmx:rmi:///jndi/rmi://localhost:0/jmxrmi" \
    --cpus 0.5 \
    --memory 256M \
    --mount type=bind,source="$(dirname "$0")/archive",destination=/opt/cryostat.d/recordings.d,relabel=shared \
    --mount type=bind,source="$(dirname "$0")/certs",destination=/certs,relabel=shared \
    --mount type=bind,source="$(dirname "$0")/clientlib",destination=/clientlib,relabel=shared \
    --mount type=bind,source="$(dirname "$0")/conf",destination=/opt/cryostat.d/conf.d,relabel=shared \
    --mount type=bind,source="$(dirname "$0")/templates",destination=/opt/cryostat.d/templates.d,relabel=shared \
    --mount type=bind,source="$(dirname "$0")/truststore",destination=/truststore,relabel=shared \
    --mount type=bind,source="$(dirname "$0")/probes",destination=/opt/cryostat.d/conf.d/probes.d,relabel=shared \
    -v "$XDG_RUNTIME_DIR"/podman/podman.sock:/run/user/0/podman/podman.sock:Z \
    --security-opt label=disable \
    -e CRYOSTAT_ENABLE_JDP_BROADCAST="true" \
    -e CRYOSTAT_REPORT_GENERATOR="$CRYOSTAT_REPORT_GENERATOR" \
    -e CRYOSTAT_PLATFORM="$CRYOSTAT_PLATFORM" \
    -e CRYOSTAT_DISABLE_BUILTIN_DISCOVERY="$CRYOSTAT_DISABLE_BUILTIN_DISCOVERY" \
    -e CRYOSTAT_DISABLE_SSL="$CRYOSTAT_DISABLE_SSL" \
    -e CRYOSTAT_DISABLE_JMX_AUTH="$CRYOSTAT_DISABLE_JMX_AUTH" \
    -e CRYOSTAT_ALLOW_UNTRUSTED_SSL="$CRYOSTAT_ALLOW_UNTRUSTED_SSL" \
    -e CRYOSTAT_RJMX_USER="$CRYOSTAT_RJMX_USER" \
    -e CRYOSTAT_RJMX_PASS="$CRYOSTAT_RJMX_PASS" \
    -e CRYOSTAT_RJMX_PORT="$CRYOSTAT_RJMX_PORT" \
    -e CRYOSTAT_RMI_PORT="$CRYOSTAT_RMI_PORT" \
    -e CRYOSTAT_CORS_ORIGIN="$CRYOSTAT_CORS_ORIGIN" \
    -e CRYOSTAT_WEB_HOST="$CRYOSTAT_WEB_HOST" \
    -e CRYOSTAT_WEB_PORT="$CRYOSTAT_WEB_PORT" \
    -e CRYOSTAT_EXT_WEB_PORT="$CRYOSTAT_EXT_WEB_PORT" \
    -e CRYOSTAT_MAX_WS_CONNECTIONS="$CRYOSTAT_MAX_WS_CONNECTIONS" \
    -e CRYOSTAT_AUTH_MANAGER="$CRYOSTAT_AUTH_MANAGER" \
    -e CRYOSTAT_TARGET_MAX_CONCURRENT_CONNECTIONS="$CRYOSTAT_TARGET_MAX_CONCURRENT_CONNECTIONS" \
    -e CRYOSTAT_TARGET_CACHE_TTL="$CRYOSTAT_TARGET_CACHE_TTL" \
    -e CRYOSTAT_CONFIG_PATH="/opt/cryostat.d/conf.d" \
    -e CRYOSTAT_ARCHIVE_PATH="/opt/cryostat.d/recordings.d" \
    -e CRYOSTAT_TEMPLATE_PATH="/opt/cryostat.d/templates.d" \
    -e CRYOSTAT_PROBE_TEMPLATE_PATH="/opt/cryostat.d/conf.d/probes.d" \
    -e CRYOSTAT_CLIENTLIB_PATH="/clientlib" \
    -e CRYOSTAT_REPORT_GENERATION_MAX_HEAP="$CRYOSTAT_REPORT_GENERATION_MAX_HEAP" \
    -e CRYOSTAT_DISCOVERY_PING_PERIOD="$CRYOSTAT_DISCOVERY_PING_PERIOD" \
    -e CRYOSTAT_ACTIVE_REPORTS_CACHE_EXPIRY_SECONDS="$CRYOSTAT_ACTIVE_REPORTS_CACHE_EXPIRY_SECONDS" \
    -e CRYOSTAT_ACTIVE_REPORTS_CACHE_REFRESH_SECONDS="$CRYOSTAT_ACTIVE_REPORTS_CACHE_REFRESH_SECONDS" \
    -e CRYOSTAT_PUSH_MAX_FILES="$CRYOSTAT_PUSH_MAX_FILES" \
    -e CRYOSTAT_VERTX_POOL_SIZE="$CRYOSTAT_VERTX_POOL_SIZE" \
    -e GRAFANA_DATASOURCE_URL="$GRAFANA_DATASOURCE_URL" \
    -e GRAFANA_DASHBOARD_URL="$GRAFANA_DASHBOARD_URL" \
    -e KEYSTORE_PATH="$KEYSTORE_PATH" \
    -e KEYSTORE_PASS="$KEYSTORE_PASS" \
    -e KEY_PATH="$KEY_PATH" \
    -e CERT_PATH="$CERT_PATH" \
    -e CRYOSTAT_JUL_CONFIG="$CRYOSTAT_JUL_CONFIG" \
    -e CRYOSTAT_JDBC_DRIVER="$CRYOSTAT_JDBC_DRIVER" \
    -e CRYOSTAT_JDBC_URL="$CRYOSTAT_JDBC_URL" \
    -e CRYOSTAT_JDBC_USERNAME="$CRYOSTAT_JDBC_USERNAME" \
    -e CRYOSTAT_JDBC_PASSWORD="$CRYOSTAT_JDBC_PASSWORD" \
    -e CRYOSTAT_JMX_CREDENTIALS_DB_PASSWORD="$CRYOSTAT_JMX_CREDENTIALS_DB_PASSWORD" \
    -e CRYOSTAT_HIBERNATE_DIALECT="$CRYOSTAT_HIBERNATE_DIALECT" \
    -e CRYOSTAT_HBM2DDL="$CRYOSTAT_HBM2DDL" \
    -e CRYOSTAT_LOG_DB_QUERIES="true" \
    -e CRYOSTAT_DEV_MODE="$CRYOSTAT_DEV_MODE" \
    --rm -it "$CRYOSTAT_IMAGE" "$@" 2>&1 | tee cryostat-run.log
