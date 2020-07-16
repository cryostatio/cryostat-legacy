#!/bin/sh

set -x
set -e

PWFILE="/tmp/jmxremote.password"
function createJmxPassword() {
    PASS="$(< /dev/urandom tr -dc _A-Z-a-z-0-9 | head -c${1:-32})"

    echo "containerjfr $PASS" > "$PWFILE"
    chmod 400 "$PWFILE"
}

if [ -z "$CONTAINER_JFR_RJMX_PORT" ]; then
    CONTAINER_JFR_RJMX_PORT=9091
fi

FLAGS=(
    "-XX:+CrashOnOutOfMemoryError"
    "-Dcom.sun.management.jmxremote.autodiscovery=true"
    "-Dcom.sun.management.jmxremote.port=$CONTAINER_JFR_RJMX_PORT"
    "-Dcom.sun.management.jmxremote.rmi.port=$CONTAINER_JFR_RJMX_PORT"
    "-Dcom.sun.management.jmxremote.ssl=false"
)

if [ -z "$CONTAINER_JFR_RJMX_AUTH" ]; then
    # default to true. This should never be disabled in production deployments
    CONTAINER_JFR_RJMX_AUTH=true
fi

if [ "$CONTAINER_JFR_RJMX_AUTH" = "true" ]; then
    createJmxPassword

    FLAGS+=("-Dcom.sun.management.jmxremote.authenticate=true")
    FLAGS+=("-Dcom.sun.management.jmxremote.password.file=$PWFILE")
    FLAGS+=("-Dcom.sun.management.jmxremote.access.file=/app/resources/jmxremote.access")
else
    FLAGS+=("-Dcom.sun.management.jmxremote.authenticate=false")
fi

java \
    "${FLAGS[@]}" \
    -cp /app/resources:/app/classes:/app/libs/* \
    com.redhat.rhjmc.containerjfr.ContainerJfr \
    "$@"
