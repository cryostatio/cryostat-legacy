#!/bin/sh

set -e

function genpass() {
    echo "$(< /dev/urandom tr -dc _A-Z-a-z-0-9 | head -c32)"
}

SSL_TRUSTSTORE_PASS="$(genpass)"

echo "$SSL_TRUSTSTORE_PASS" > "$SSL_TRUSTSTORE_PASS_FILE"

trap popd EXIT
pushd $CONF_DIR

keytool -importkeystore \
    -noprompt \
    -storetype PKCS12 \
    -srckeystore /usr/lib/jvm/java-11-openjdk/lib/security/cacerts \
    -srcstorepass changeit \
    -destkeystore "$SSL_TRUSTSTORE" \
    -deststorepass "$SSL_TRUSTSTORE_PASS"
