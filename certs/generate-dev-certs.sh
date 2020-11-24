#!/bin/sh

set -x
set -e

function genpass() {
    echo "$(< /dev/urandom tr -dc _A-Z-a-z-0-9 | head -c32)"
}

CERTS_DIR=$(dirname $0)

SSL_TRUSTSTORE=container-jfr-truststore.p12

SSL_TRUSTSTORE_PASS=$(genpass)

SSL_KEYSTORE=container-jfr-keystore.p12

SSL_KEYSTORE_PASS=$(genpass)

SSL_KEYSTORE_PASS_FILE=keystore.pass

pushd $CERTS_DIR
trap popd EXIT

set +e
rm $SSL_TRUSTSTORE $SSL_KEYSTORE $SSL_KEYSTORE_PASS_FILE
set -e

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
    -alias custom-container-jfr \
    -dname "cn=container-jfr, o=Red Hat, c=US" \
    -storetype PKCS12 \
    -validity 365 \
    -keyalg RSA \
    -storepass "$SSL_KEYSTORE_PASS" \
    -keystore "$SSL_KEYSTORE"

keytool \
    -exportcert -v \
    -alias custom-container-jfr \
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

rm server.cer
