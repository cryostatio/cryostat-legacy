#!/bin/sh

exec java \
    -Dorg.openjdk.jmc.common.security.manager=es.andrewazor.containertest.jmc.SecurityManager \
    -Des.andrewazor.containertest.download.host=$CONTAINER_DOWNLOAD_HOST \
    -Des.andrewazor.containertest.download.port=$CONTAINER_DOWNLOAD_PORT \
    -cp container-test.jar \
    es.andrewazor.containertest.JMXClient \
    "$@"
