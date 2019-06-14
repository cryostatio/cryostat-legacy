#!/bin/sh

set -x

function cleanup() {
    set +e
    # TODO: better container management
    docker kill $(docker ps -a -q --filter ancestor=quay.io/rh-jmc-team/container-jfr)
    docker rm $(docker ps -a -q --filter ancestor=quay.io/rh-jmc-team/container-jfr)
}

cleanup
trap cleanup EXIT

set +e
docker network create --attachable container-jfr
set -e

docker run \
    --net container-jfr \
    --hostname container-jfr \
    --name container-jfr \
    --memory 80M \
    --cpus 1.0 \
    --mount source=flightrecordings,target=/flightrecordings \
    -p 9090:9090 \
    -p 8090:8080 \
    -e CONTAINER_JFR_DOWNLOAD_HOST=$CONTAINER_JFR_DOWNLOAD_HOST \
    -e CONTAINER_JFR_DOWNLOAD_PORT=$CONTAINER_JFR_DOWNLOAD_PORT \
    --rm -it quay.io/rh-jmc-team/container-jfr "$@"
