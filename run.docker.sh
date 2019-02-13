#!/bin/bash

set -x

function cleanup() {
    set +e
    # TODO: better container management
    docker kill $(docker ps -a -q --filter ancestor=docker.io/andrewazores/container-jmx-listener)
    docker kill $(docker ps -a -q --filter ancestor=docker.io/andrewazores/container-jmx-client)
    docker rm $(docker ps -a -q --filter ancestor=docker.io/andrewazores/container-jmx-listener)
    docker rm $(docker ps -a -q --filter ancestor=docker.io/andrewazores/container-jmx-client)
}

cleanup
trap cleanup EXIT

set +e
docker network create --attachable jmx-test
set -e

docker run --rm --net=jmx-test --name jmx-listener -p 9090:9090 -d docker.io/andrewazores/container-jmx-listener

docker run \
    --net=jmx-test \
    -e CONTAINER_DOWNLOAD_HOST=$CONTAINER_DOWNLOAD_HOST \
    -e CONTAINER_DOWNLOAD_PORT=$CONTAINER_DOWNLOAD_PORT \
    --rm -it docker.io/andrewazores/container-jmx-client "$@"