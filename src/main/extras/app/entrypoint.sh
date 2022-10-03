#!/bin/sh

set -e

banner() {
    echo   "+------------------------------------------+"
    printf "| %-40s |\n" "$(date)"
    echo   "|                                          |"
    printf "| %-40s |\n" "$@"
    echo   "+------------------------------------------+"
}

genpass() {
    printf '%s' "$(< /dev/urandom tr -dc _A-Z-a-z-0-9 | head -c32)"
}

USRFILE="/tmp/jmxremote.access"
PWFILE="/tmp/jmxremote.password"
createJmxCredentials() {
    if [ -z "$CRYOSTAT_RJMX_USER" ]; then
        CRYOSTAT_RJMX_USER="cryostat"
    fi
    if [ -z "$CRYOSTAT_RJMX_PASS" ]; then
        CRYOSTAT_RJMX_PASS="$(genpass)"
    fi

    printf '%s %s' "$CRYOSTAT_RJMX_USER" "$CRYOSTAT_RJMX_PASS" > "$PWFILE"
    chmod 400 "$PWFILE"
    printf '%s readwrite' "$CRYOSTAT_RJMX_USER" > "$USRFILE"
    chmod 400 "$USRFILE"
}

if [ -z "$CONF_DIR" ]; then
    # this should be set by Containerfile, but set a default if not
    CONF_DIR="/opt/cryostat.d"
fi
export SSL_KEYSTORE="$CONF_DIR/keystore.p12"
export SSL_STORE_PASS="$SSL_KEY_PASS"
SSL_KEY_PASS="$(genpass)"
export SSL_KEY_PASS
SSL_TRUSTSTORE_PASS="$(cat "$SSL_TRUSTSTORE_PASS_FILE")"
export SSL_TRUSTSTORE_PASS

if [ -z "$SSL_TRUSTSTORE_DIR" ]; then
    SSL_TRUSTSTORE_DIR="/truststore"
fi
export SSL_TRUSTSTORE_DIR

importTrustStores() {
    if [ ! -d "$SSL_TRUSTSTORE_DIR" ]; then
        banner "$SSL_TRUSTSTORE_DIR does not exist; no certificates to import"
        return 0
    elif [ ! "$(ls -A $SSL_TRUSTSTORE_DIR)" ]; then
        banner "$SSL_TRUSTSTORE_DIR is empty; no certificates to import"
        return 0
    fi

    find "./truststore" -type f | while IFS= read -r cert; do
        echo "Importing certificate $cert ..."

        keytool -importcert -v \
            -noprompt \
            -alias "imported-$(basename "$cert")" \
            -trustcacerts \
            -keystore "$SSL_TRUSTSTORE" \
            -file "$cert"\
            -storepass "$SSL_TRUSTSTORE_PASS"
    done
}

generateSslCert() {
    cd /tmp

    keytool -genkeypair -v \
        -alias cryostat \
        -dname "cn=cryostat, o=Cryostat, c=CA" \
        -storetype PKCS12 \
        -validity 180 \
        -keyalg RSA \
        -keypass "$SSL_KEY_PASS" \
        -storepass "$SSL_STORE_PASS" \
        -keystore "$SSL_KEYSTORE"

    keytool -exportcert -v \
        -alias cryostat \
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

    cd -
}

if [ -z "$CRYOSTAT_RJMX_PORT" ]; then
    CRYOSTAT_RJMX_PORT=9091
fi

if [ -z "$CRYOSTAT_RMI_PORT" ]; then
    CRYOSTAT_RMI_PORT=9091
fi

FLAGS="-XX:+CrashOnOutOfMemoryError -Dcom.sun.management.jmxremote.port=$CRYOSTAT_RJMX_PORT -Dcom.sun.management.jmxremote.rmi.port=$CRYOSTAT_RMI_PORT -Djavax.net.ssl.trustStore=$SSL_TRUSTSTORE -Djavax.net.ssl.trustStorePassword=$SSL_TRUSTSTORE_PASS"


if [ -z "$CRYOSTAT_ENABLE_JDP_BROADCAST" ]; then
    FLAGS="${FLAGS} -Dcom.sun.management.jmxremote.autodiscovery=true"
else
    FLAGS="${FLAGS} -Dcom.sun.management.jmxremote.autodiscovery=$CRYOSTAT_ENABLE_JDP_BROADCAST"
fi

if [ -n "$CRYOSTAT_JDP_ADDRESS" ]; then
    FLAGS="${FLAGS} -Dcom.sun.management.jmxremote.jdp.address=$CRYOSTAT_JDP_ADDRESS"
fi

if [ -n "$CRYOSTAT_JDP_PORT" ]; then
    FLAGS="${FLAGS} -Dcom.sun.management.jmxremote.jdp.port=$CRYOSTAT_JDP_PORT"
fi

importTrustStores

if [ "$CRYOSTAT_DISABLE_JMX_AUTH" = "true" ]; then
    banner "JMX Auth Disabled"
    FLAGS="${FLAGS} -Dcom.sun.management.jmxremote.authenticate=false"
else
    createJmxCredentials
    FLAGS="${FLAGS} -Dcom.sun.management.jmxremote.authenticate=true"
    FLAGS="${FLAGS} -Dcom.sun.management.jmxremote.password.file=$PWFILE"
    FLAGS="${FLAGS} -Dcom.sun.management.jmxremote.access.file=$USRFILE"
fi

if [ "$CRYOSTAT_DISABLE_SSL" = "true" ]; then
    banner "SSL Disabled"
    FLAGS="${FLAGS} -Dcom.sun.management.jmxremote.ssl=false"
    FLAGS="${FLAGS} -Dcom.sun.management.jmxremote.registry.ssl=false"
else
    FLAGS="${FLAGS} -Dcom.sun.management.jmxremote.ssl.need.client.auth=true"

    if [ -z "$KEYSTORE_PATH" ] || [ -z "$KEYSTORE_PASS" ]; then
        generateSslCert
        banner "Using self-signed SSL certificate"

        KEYSTORE_PATH="$SSL_KEYSTORE"
        KEYSTORE_PASS="$SSL_KEY_PASS"
    fi

    FLAGS="${FLAGS} -Djavax.net.ssl.keyStore=$KEYSTORE_PATH"
    FLAGS="${FLAGS} -Djavax.net.ssl.keyStorePassword=$KEYSTORE_PASS"
    FLAGS="${FLAGS} -Dcom.sun.management.jmxremote.ssl=true"
    FLAGS="${FLAGS} -Dcom.sun.management.jmxremote.registry.ssl=true"
fi

if [ -n "$CRYOSTAT_JUL_CONFIG" ]; then
    FLAGS="${FLAGS} -Djava.util.logging.config.file=$CRYOSTAT_JUL_CONFIG"
fi

CLASSPATH="$( cat /app/jib-classpath-file )"
if [ -n "$CRYOSTAT_CLIENTLIB_PATH" ]; then
    CLASSPATH="$CLASSPATH:$CRYOSTAT_CLIENTLIB_PATH/*"
fi

export KEYSTORE_PATH
export KEYSTORE_PASS
export SSL_TRUSTSTORE_DIR

set -x
exec java \
    "${FLAGS}" \
    -cp "$CLASSPATH" \
    @/app/jib-main-class-file \
    "$@"
