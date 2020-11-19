#!/bin/sh

set -e

SSL_TRUSTSTORE_PASS="$(cat $SSL_TRUSTSTORE_PASS_FILE)"

trap popd EXIT
pushd $CONF_DIR

keytool -genkeypair -v \
    -alias container-jfr \
    -dname "cn=container-jfr, o=Red Hat, c=US" \
    -storetype PKCS12 \
    -validity 365 \
    -keyalg RSA \
    -storepass "$SSL_TRUSTSTORE_PASS" \
    -keystore "$SSL_KEYSTORE"

keytool -exportcert -v \
    -alias container-jfr \
    -keystore "$SSL_KEYSTORE" \
    -storepass "$SSL_TRUSTSTORE_PASS" \
    -file server.cer

keytool -importcert -v \
    -noprompt \
    -trustcacerts \
    -keystore "$SSL_TRUSTSTORE" \
    -alias selftrust \
    -file server.cer \
    -storepass "$SSL_TRUSTSTORE_PASS"
