#!/bin/sh

set -x
set -e

USRFILE="/tmp/jmxremote.access"
PWFILE="/tmp/jmxremote.password"
function createJmxCredentials() {
    if [ -z "$CONTAINER_JFR_RJMX_USER" ]; then
        CONTAINER_JFR_RJMX_USER="containerjfr"
    fi
    if [ -z "$CONTAINER_JFR_RJMX_PASS" ]; then 
        CONTAINER_JFR_RJMX_PASS="$(< /dev/urandom tr -dc _A-Z-a-z-0-9 | head -c32)"
    fi

    echo "$CONTAINER_JFR_RJMX_USER $CONTAINER_JFR_RJMX_PASS" > "$PWFILE"
    chmod 400 "$PWFILE"
    echo "$CONTAINER_JFR_RJMX_USER readwrite" > "$USRFILE"
    chmod 400 "$USRFILE"
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

if [ "$CONTAINER_JFR_RJMX_AUTH" = "true" ] || [ -n "$CONTAINER_JFR_RJMX_USER" ] || 
    [ -n "$CONTAINER_JFR_RJMX_PASS" ]; then
    createJmxCredentials

    FLAGS+=("-Dcom.sun.management.jmxremote.authenticate=true")
    FLAGS+=("-Dcom.sun.management.jmxremote.password.file=$PWFILE")
    FLAGS+=("-Dcom.sun.management.jmxremote.access.file=$USRFILE")
else
    FLAGS+=("-Dcom.sun.management.jmxremote.authenticate=false")
fi

java \
    "${FLAGS[@]}" \
    -cp /app/resources:/app/classes:/app/libs/* \
    com.redhat.rhjmc.containerjfr.ContainerJfr \
    "$@"
