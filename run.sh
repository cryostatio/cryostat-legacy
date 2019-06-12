#!/bin/sh

set -x

function cleanup() {
    set +e
    # TODO: better container management
    docker kill $(docker ps -a -q --filter ancestor=andrewazores/container-jmx-client)
    docker rm $(docker ps -a -q --filter ancestor=andrewazores/container-jmx-client)
}

cleanup
trap cleanup EXIT

set +e
docker network create --attachable jmx-test
set -e

docker run \
    --net jmx-test \
    --hostname jmx-client \
    --name jmx-client \
    --memory 80M \
    --cpus 1.0 \
    --mount source=flightrecordings,target=/flightrecordings \
    -p 9090:9090 \
    -e CONTAINER_DOWNLOAD_HOST=$CONTAINER_DOWNLOAD_HOST \
    -e CONTAINER_DOWNLOAD_PORT=$CONTAINER_DOWNLOAD_PORT \
    --rm -it andrewazores/container-jmx-client "$@"
