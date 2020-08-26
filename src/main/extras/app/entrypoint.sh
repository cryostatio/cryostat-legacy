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

SSL_KEYSTORE="/tmp/keystore.p12"
SSL_KEY_PASS="$(genpass)"
SSL_STORE_PASS="$SSL_KEY_PASS"
SSL_TRUSTSTORE="/tmp/truststore.p12"
SSL_TRUSTSTORE_PASS="$(genpass)"
function createSslStores() {
    pushd /tmp

    keytool -importkeystore \
        -noprompt \
        -storetype PKCS12 \
        -srckeystore /usr/lib/jvm/java-11-openjdk/lib/security/cacerts \
        -srcstorepass changeit \
        -destkeystore "$SSL_TRUSTSTORE" \
        -deststorepass "$SSL_TRUSTSTORE_PASS"

    popd
}

function importTrustStores() {
    local DIR="/truststore"
    if [ ! -d "$DIR" ]; then
        echo "$DIR does not exist; no certificates to import"
        return 0
    elif [ ! "$(ls -A $DIR)" ]; then
        echo "$DIR is empty; no certificates to import"
        return 0
    fi

    for cert in $(find "$DIR" -type f); do
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

function generateSslCert() {
    pushd /tmp

    keytool -genkeypair -v \
        -alias container-jfr \
        -dname "cn=container-jfr, o=Red Hat, c=US" \
        -storetype PKCS12 \
        -validity 180 \
        -keyalg RSA \
        -keypass "$SSL_KEY_PASS" \
        -storepass "$SSL_STORE_PASS" \
        -keystore "$SSL_KEYSTORE"

    keytool -exportcert -v \
        -alias container-jfr \
        -keystore "$SSL_KEYSTORE" \
        -storepass "$SSL_STORE_PASS" \
        -file server.cer

    keytool -importcert -v \
        -noprompt \
        -trustcacerts \
        -keystore "$SSL_TRUSTSTORE" \
        -alias selftrust \
        -file server.cer \
        -storepass "$SSL_TRUSTSTORE_PASS"

    popd
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

createSslStores
importTrustStores

if [ "$CONTAINER_JFR_FORCE_INSECURE" = "true" ] && [ -z "$CONTAINER_JFR_RJMX_USER" ] && [ -z "$CONTAINER_JFR_RJMX_PASS" ]; then
    FLAGS+=("-Dcom.sun.management.jmxremote.authenticate=false")
    FLAGS+=("-Dcom.sun.management.jmxremote.ssl=false")
    FLAGS+=("-Dcom.sun.management.jmxremote.registry.ssl=false")
else
    createJmxCredentials

    FLAGS+=("-Dcom.sun.management.jmxremote.authenticate=true")
    FLAGS+=("-Dcom.sun.management.jmxremote.ssl.need.client.auth=true")
    FLAGS+=("-Dcom.sun.management.jmxremote.password.file=$PWFILE")
    FLAGS+=("-Dcom.sun.management.jmxremote.access.file=$USRFILE")

    if [ -z "$KEYSTORE_PATH" ] || [ -z "$KEYSTORE_PASS" ]; then
        generateSslCert

        KEYSTORE_PATH="$SSL_KEYSTORE"
        KEYSTORE_PASS="$SSL_KEY_PASS"
    fi

    FLAGS+=("-Djavax.net.ssl.keyStore=$SSL_KEYSTORE")
    FLAGS+=("-Djavax.net.ssl.keyStorePassword=$SSL_KEY_PASS")
    FLAGS+=("-Dcom.sun.management.jmxremote.ssl=true")
    FLAGS+=("-Dcom.sun.management.jmxremote.registry.ssl=true")
fi

KEYSTORE_PATH="$KEYSTORE_PATH" \
    KEYSTORE_PASS="$KEYSTORE_PASS" \
    java \
    "${FLAGS[@]}" \
    -cp /app/resources:/app/classes:/app/libs/* \
    com.redhat.rhjmc.containerjfr.ContainerJfr \
    "$@"
