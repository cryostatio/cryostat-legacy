#!/bin/sh

set -e

genpass() {
    < /dev/urandom tr -dc _A-Z-a-z-0-9 | head -c32
}

SSL_TRUSTSTORE_PASS="$(genpass)"

echo "$SSL_TRUSTSTORE_PASS" > "$SSL_TRUSTSTORE_PASS_FILE"

trap "cd -" EXIT
cd "$CONF_DIR"

keytool -importkeystore \
    -noprompt \
    -storetype PKCS12 \
    -srckeystore /usr/lib/jvm/jre-17-openjdk/lib/security/cacerts \
    -srcstorepass changeit \
    -destkeystore "$SSL_TRUSTSTORE" \
    -deststorepass "$SSL_TRUSTSTORE_PASS"
