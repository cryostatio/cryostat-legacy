#!/bin/sh

exec java \
    -Dorg.openjdk.jmc.common.security.manager=es.andrewazor.containertest.jmc.SecurityManager \
    -Des.andrewazor.containertest.download.host=$CONTAINER_DOWNLOAD_HOST \
    -Des.andrewazor.containertest.download.port=$CONTAINER_DOWNLOAD_PORT \
    -Dcom.sun.management.jmxremote.rmi.port=9091 \
    -Dcom.sun.management.jmxremote=true \
    -Dcom.sun.management.jmxremote.port=9091 \
    -Dcom.sun.management.jmxremote.ssl=false \
    -Dcom.sun.management.jmxremote.authenticate=false \
    -Dcom.sun.management.jmxremote.local.only=false \
    -Djava.rmi.server.hostname=jmx-client \
    -cp container-test.jar \
    es.andrewazor.containertest.JMXClient \
    "$@"
