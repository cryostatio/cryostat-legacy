#!/bin/sh

set -e

function banner() {
    echo   "+------------------------------------------+"
    printf "| %-40s |\n" "`date`"
    echo   "|                                          |"
    printf "| %-40s |\n" "$@"
    echo   "+------------------------------------------+"
}

USRFILE="/tmp/jmxremote.access"
PWFILE="/tmp/jmxremote.password"
function createJmxCredentials() {
    if [ -z "$CONTAINER_JFR_RJMX_USER" ]; then
        CONTAINER_JFR_RJMX_USER="containerjfr"
    fi
    if [ -z "$CONTAINER_JFR_RJMX_PASS" ]; then
        CONTAINER_JFR_RJMX_PASS="$(genpass)"
    fi

    echo -n "$CONTAINER_JFR_RJMX_USER $CONTAINER_JFR_RJMX_PASS" > "$PWFILE"
    chmod 400 "$PWFILE"
    echo -n "$CONTAINER_JFR_RJMX_USER readwrite" > "$USRFILE"
    chmod 400 "$USRFILE"
}

export SSL_TRUSTSTORE_PASS="$(cat $SSL_TRUSTSTORE_PASS_FILE)"

if [ -z "$SSL_TRUSTSTORE_DIR" ]; then
    SSL_TRUSTSTORE_DIR="/truststore"
fi
export SSL_TRUSTSTORE_DIR

function importTrustStores() {
    if [ ! -d "$SSL_TRUSTSTORE_DIR" ]; then
        banner "$SSL_TRUSTSTORE_DIR does not exist; no certificates to import"
        return 0
    elif [ ! "$(ls -A $SSL_TRUSTSTORE_DIR)" ]; then
        banner "$SSL_TRUSTSTORE_DIR is empty; no certificates to import"
        return 0
    fi

    for cert in $(find "$SSL_TRUSTSTORE_DIR" -type f); do
        echo "Importing certificate $cert ..."

        keytool -importcert -v \
            -noprompt \
            -alias "imported-$(basename $cert)" \
            -trustcacerts \
            -keystore "$SSL_TRUSTSTORE" \
            -file "$cert"\
            -storepass "$SSL_TRUSTSTORE_PASS"
    done
}

if [ -z "$CONTAINER_JFR_RJMX_PORT" ]; then
    CONTAINER_JFR_RJMX_PORT=9091
fi

if [ -z "$CONTAINER_JFR_RMI_PORT" ]; then
    CONTAINER_JFR_RMI_PORT=9091
fi

FLAGS=(
    "-XX:+CrashOnOutOfMemoryError"
    "-Dcom.sun.management.jmxremote.autodiscovery=true"
    "-Dcom.sun.management.jmxremote.port=$CONTAINER_JFR_RJMX_PORT"
    "-Dcom.sun.management.jmxremote.rmi.port=$CONTAINER_JFR_RMI_PORT"
    "-Djavax.net.ssl.trustStore=$SSL_TRUSTSTORE"
    "-Djavax.net.ssl.trustStorePassword=$SSL_TRUSTSTORE_PASS"
)

importTrustStores

if [ "$CONTAINER_JFR_DISABLE_JMX_AUTH" = "true" ]; then
    banner "JMX Auth Disabled"
    FLAGS+=("-Dcom.sun.management.jmxremote.authenticate=false")
else
    createJmxCredentials
    FLAGS+=("-Dcom.sun.management.jmxremote.authenticate=true")
    FLAGS+=("-Dcom.sun.management.jmxremote.password.file=$PWFILE")
    FLAGS+=("-Dcom.sun.management.jmxremote.access.file=$USRFILE")
fi

if [ "$CONTAINER_JFR_DISABLE_SSL" = "true" ]; then
    banner "SSL Disabled"
    FLAGS+=("-Dcom.sun.management.jmxremote.ssl=false")
    FLAGS+=("-Dcom.sun.management.jmxremote.registry.ssl=false")
else
    FLAGS+=("-Dcom.sun.management.jmxremote.ssl.need.client.auth=true")

    if [ -z "$KEYSTORE_PATH" ] || [ -z "$KEYSTORE_PASS" ]; then
        banner "Using self-signed SSL certificate"

        KEYSTORE_PATH="$SSL_KEYSTORE"
        KEYSTORE_PASS="$SSL_TRUSTSTORE_PASS"
    fi

    FLAGS+=("-Djavax.net.ssl.keyStore=$KEYSTORE_PATH")
    FLAGS+=("-Djavax.net.ssl.keyStorePassword=$KEYSTORE_PASS")
    FLAGS+=("-Dcom.sun.management.jmxremote.ssl=true")
    FLAGS+=("-Dcom.sun.management.jmxremote.registry.ssl=true")
fi

if [ -n "$CONTAINER_JFR_JUL_CONFIG" ]; then
    FLAGS+=("-Djava.util.logging.config.file=$CONTAINER_JFR_JUL_CONFIG")
fi

export KEYSTORE_PATH
export KEYSTORE_PASS
export SSL_TRUSTSTORE_DIR
exec java \
    "${FLAGS[@]}" \
    -cp /app/resources:/app/classes:/app/libs/* \
    com.redhat.rhjmc.containerjfr.ContainerJfr \
    "$@"
