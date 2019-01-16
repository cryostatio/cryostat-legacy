#!/bin/sh

# TODO: better Docker container management
docker kill $(docker ps -a -q --filter ancestor=docker-jmx-test)
docker rm $(docker ps -a -q --filter ancestor=docker-jmx-test)

set -e

docker run -d -p 9090:9090 -p 9091:9091 docker-jmx-test
echo "Waiting for start"
sleep 2
pushd build/libs
java \
    -Dorg.openjdk.jmc.common.security.manager="es.andrewazor.dockertest.jmc.SecurityManager" \
    -cp docker-test.jar \
    es.andrewazor.dockertest.JMXClient "$@"
popd
