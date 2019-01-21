#!/bin/sh

set -x

function docker_cleanup() {
    set +e
    # TODO: better Docker container management
    docker kill $(docker ps -a -q --filter ancestor=docker-jmx-listener)
    docker kill $(docker ps -a -q --filter ancestor=docker-jmx-client)
    docker rm $(docker ps -a -q --filter ancestor=docker-jmx-listener)
    docker rm $(docker ps -a -q --filter ancestor=docker-jmx-client)
}

docker_cleanup
trap docker_cleanup EXIT

docker network create --attachable docker-jmx-test

set -e

RECORDING_DIR="$(pwd)/recordings"
mkdir -p "$RECORDING_DIR"

docker run --rm --net=docker-jmx-test --name docker-jmx-listener -p 9090:9090 -d docker-jmx-listener
echo "Waiting for start"
# TODO: better detection of container startup
sleep 2
pushd build/libs
docker run --rm --net=docker-jmx-test -u "$(id -u)" -v "$RECORDING_DIR:/recordings" docker-jmx-client "$@"
popd
