#!/bin/sh

# Used by pom.xml to generate a ContainerJFR jmxremote.password file for securing remote JMX connections

LEN=16
PASS="$(openssl rand -base64 $LEN)"

echo "containerjfr $PASS" > src/main/extras/app/resources/jmxremote.password
