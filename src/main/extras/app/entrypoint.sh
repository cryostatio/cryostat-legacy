#!/bin/sh

set -x
set -e

function genpass() {
    echo "$(< /dev/urandom tr -dc _A-Z-a-z-0-9 | head -c32)"
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

    echo "$CONTAINER_JFR_RJMX_USER $CONTAINER_JFR_RJMX_PASS" > "$PWFILE"
    chmod 400 "$PWFILE"
    echo "$CONTAINER_JFR_RJMX_USER readwrite" > "$USRFILE"
    chmod 400 "$USRFILE"
}

SSL_KEYSTORE="/tmp/keystore.jks"
SSL_KEY_PASS="$(genpass)"
SSL_STORE_PASS="$SSL_KEY_PASS"
function createSslStores() {
    pushd /tmp

    keytool -genkeypair \
        -alias container-jfr \
        -dname "cn=container-jfr, o=Red Hat, c=US" \
        -validity 180 \
        -keyalg RSA \
        -keypass "$SSL_KEY_PASS" \
        -storepass "$SSL_STORE_PASS" \
        -keystore "$SSL_KEYSTORE"

    keytool -export \
        -alias container-jfr \
        -keystore "$SSL_KEYSTORE" \
        -storepass "$SSL_STORE_PASS" \
        -file server.cer

    keytool -import -v \
        -noprompt \
        -trustcacerts \
        -alias selftrust \
        -file server.cer \
        -keypass "$SSL_KEY_PASS" \
        -storepass "$SSL_STORE_PASS"

    popd
}

if [ -z "$CONTAINER_JFR_RJMX_PORT" ]; then
    CONTAINER_JFR_RJMX_PORT=9091
fi

if [ -z "$CONTAINER_JFR_RMI_PORT" ]; then
    CONTAINER_JFR_RMI_PORT=9092
fi

FLAGS=(
    "-XX:+CrashOnOutOfMemoryError"
    "-Dcom.sun.management.jmxremote.autodiscovery=true"
    "-Dcom.sun.management.jmxremote.port=$CONTAINER_JFR_RJMX_PORT"
    "-Dcom.sun.management.jmxremote.rmi.port=$CONTAINER_JFR_RMI_PORT"
)

if [ -z "$CONTAINER_JFR_RJMX_AUTH" ]; then
    # default to true. This should never be disabled in production deployments
    CONTAINER_JFR_RJMX_AUTH=true
fi

if [ "$CONTAINER_JFR_RJMX_AUTH" = "true" ] || [ -n "$CONTAINER_JFR_RJMX_USER" ] ||
    [ -n "$CONTAINER_JFR_RJMX_PASS" ]; then
    createJmxCredentials
    createSslStores

    FLAGS+=("-Dcom.sun.management.jmxremote.authenticate=true")
    FLAGS+=("-Dcom.sun.management.jmxremote.password.file=$PWFILE")
    FLAGS+=("-Dcom.sun.management.jmxremote.access.file=$USRFILE")
    FLAGS+=("-Dcom.sun.management.jmxremote.ssl=true")
    FLAGS+=("-Dcom.sun.management.jmxremote.registry.ssl=false")
    FLAGS+=("-Dcom.sun.management.jmxremote.ssl.need.client.auth=false")
    FLAGS+=("-Djavax.net.ssl.keyStore=$SSL_KEYSTORE")
    FLAGS+=("-Djavax.net.ssl.keyStorePassword=$SSL_KEY_PASS")
else
    FLAGS+=("-Dcom.sun.management.jmxremote.authenticate=false")
    FLAGS+=("-Dcom.sun.management.jmxremote.ssl=false")
    FLAGS+=("-Dcom.sun.management.jmxremote.registry.ssl=false")
fi

java \
    "${FLAGS[@]}" \
    -cp /app/resources:/app/classes:/app/libs/* \
    com.redhat.rhjmc.containerjfr.ContainerJfr \
    "$@"
