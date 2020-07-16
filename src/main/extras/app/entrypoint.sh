#!/bin/sh

set -x
set -e

PWFILE="/tmp/jmxremote.password"
function createJmxPassword() {
    PASS="$(< /dev/urandom tr -dc _A-Z-a-z-0-9 | head -c${1:-32})"

    touch $PWFILE
    echo "containerjfr $PASS" > "$PWFILE"
    chmod 400 "$PWFILE"
}

createJmxPassword

if [ -z "$CONTAINER_JFR_RJMX_PORT" ]; then
    CONTAINER_JFR_RJMX_PORT=9091
fi

FLAGS=(
    "-XX:+CrashOnOutOfMemoryError"
    "-Dcom.sun.management.jmxremote.autodiscovery=true"
    "-Dcom.sun.management.jmxremote.port=$CONTAINER_JFR_RJMX_PORT"
    "-Dcom.sun.management.jmxremote.rmi.port=$CONTAINER_JFR_RJMX_PORT"
    "-Dcom.sun.management.jmxremote.ssl=false"
    "-Dcom.sun.management.jmxremote.authenticate=true"
    "-Dcom.sun.management.jmxremote.password.file=$PWFILE"
    "-Dcom.sun.management.jmxremote.access.file=/app/resources/jmxremote.access"
)

java \
    "${FLAGS[@]}" \
    -cp /app/resources:/app/classes:/app/libs/* \
    com.redhat.rhjmc.containerjfr.ContainerJfr \
    "$@"
