#!/bin/sh

set -x
set -e

function createJmxPassword() {
    PWFILE="/app/resources/jmxremote.password"
    PASS="$(< /dev/urandom tr -dc _A-Z-a-z-0-9 | head -c${1:-32})"

    echo "containerjfr $PASS" > "$PWFILE"
    chmod 400 "$PWFILE"
}

createJmxPassword

FLAGS=(
    "-XX:+CrashOnOutOfMemoryError"
    "-Dcom.sun.management.jmxremote.autodiscovery=true"
    "-Dcom.sun.management.jmxremote.port=$CONTAINER_JFR_RJMX_PORT"
    "-Dcom.sun.management.jmxremote.rmi.port=$CONTAINER_JFR_RJMX_PORT"
    "-Dcom.sun.management.jmxremote.ssl=false"
    "-Dcom.sun.management.jmxremote.authenticate=true"
    "-Dcom.sun.management.jmxremote.password.file=/app/resources/jmxremote.password"
    "-Dcom.sun.management.jmxremote.access.file=/app/resources/jmxremote.access"
)

java \
    "${FLAGS[@]}" \
    -cp /app/resources:/app/classes:/app/libs/* \
    com.redhat.rhjmc.containerjfr.ContainerJfr \
    "$@"
