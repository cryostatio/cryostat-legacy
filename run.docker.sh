#!/bin/bash

set -x

function cleanup() {
    set +e
    # TODO: better container management
    docker kill $(docker ps -a -q --filter ancestor=docker.io/andrewazores/container-jmx-listener-podman)
    docker kill $(docker ps -a -q --filter ancestor=docker.io/andrewazores/container-jmx-listener-docker)
    docker kill $(docker ps -a -q --filter ancestor=docker.io/andrewazores/container-jmx-client)
    docker rm $(docker ps -a -q --filter ancestor=docker.io/andrewazores/container-jmx-listener-podman)
    docker rm $(docker ps -a -q --filter ancestor=docker.io/andrewazores/container-jmx-listener-docker)
    docker rm $(docker ps -a -q --filter ancestor=docker.io/andrewazores/container-jmx-client)
}

cleanup
trap cleanup EXIT

set +e
docker network create --attachable docker-jmx-test
set -e

RECORDING_DIR="$(pwd)/recordings"
mkdir -p "$RECORDING_DIR"

docker run --rm --net=docker-jmx-test --name container-jmx-listener -p 9090:9090 -d docker.io/andrewazores/container-jmx-listener-docker
echo "Waiting for start"
# TODO: better detection of container startup
sleep 2
pushd build/libs
docker run --rm --net=docker-jmx-test -it -u "$(id -u)" -v "$RECORDING_DIR:/recordings" docker.io/andrewazores/container-jmx-client "$@"
popd