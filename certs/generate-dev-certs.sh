#!/bin/sh

set -x

CERTS_DIR=$(realpath "$(dirname "$0")")

SSL_KEYSTORE=cryostat-keystore.p12

SSL_TRUSTSTORE=cryostat-truststore.p12

SSL_KEYSTORE_PASS_FILE=keystore.pass

cleanup() {
    cd "$CERTS_DIR"
    rm $SSL_TRUSTSTORE $SSL_KEYSTORE $SSL_KEYSTORE_PASS_FILE
    cd -
}

case "$1" in
    clean)
        cleanup
        exit 0
        ;;
    generate)
        ;;
    *)
        echo "Usage: $0 [clean|generate]"
        exit 1
        ;;
esac

set -e

genpass() {
    < /dev/urandom tr -dc _A-Z-a-z-0-9 | head -c32
}

SSL_TRUSTSTORE_PASS=$(genpass)

SSL_KEYSTORE_PASS=$(genpass)

cd "$CERTS_DIR"
trap "cd -" EXIT

echo "$SSL_KEYSTORE_PASS" > $SSL_KEYSTORE_PASS_FILE

keytool \
    -importkeystore \
    -noprompt \
    -storetype PKCS12 \
    -srckeystore /usr/lib/jvm/java-11-openjdk/lib/security/cacerts \
    -srcstorepass changeit \
    -destkeystore "$SSL_TRUSTSTORE" \
    -deststorepass "$SSL_TRUSTSTORE_PASS"

keytool \
    -genkeypair -v \
    -alias custom-cryostat \
    -dname "cn=cryostat, o=Cryostat, c=CA" \
    -storetype PKCS12 \
    -validity 365 \
    -keyalg RSA \
    -storepass "$SSL_KEYSTORE_PASS" \
    -keystore "$SSL_KEYSTORE"

keytool \
    -exportcert -v \
    -alias custom-cryostat \
    -keystore "$SSL_KEYSTORE" \
    -storepass "$SSL_KEYSTORE_PASS" \
    -file server.cer

keytool \
    -importcert -v \
    -noprompt \
    -trustcacerts \
    -keystore "$SSL_TRUSTSTORE" \
    -alias selftrust \
    -file server.cer \
    -storepass "$SSL_TRUSTSTORE_PASS"

mv server.cer "$CERTS_DIR/../truststore/dev-self-signed.cer"
