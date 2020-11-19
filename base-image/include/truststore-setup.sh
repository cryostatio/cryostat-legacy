#!/bin/sh

set -e

source genpass.sh

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
