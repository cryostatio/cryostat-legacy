#!/bin/sh

# TODO: better Docker container management
docker kill $(docker ps -a -q --filter ancestor=docker-test)
docker rm $(docker ps -a -q --filter ancestor=docker-test)

set -e

docker run -d -p 9090:9090 -p 9091:9091 -v mnt:/jfr docker-test
echo "Waiting for start"
sleep 2
pushd build/libs
java \
    -Ddockertest.pwd="$DOCKER_TEST_PWD" \
    -Dorg.openjdk.jmc.common.security.manager="es.andrewazor.dockertest.SecurityManager" \
    -cp docker-test.jar \
    es.andrewazor.dockertest.JMXClient
popd
