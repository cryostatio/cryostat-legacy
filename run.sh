#!/bin/bash

set -x

if [ -z "$CMD" ]; then
    CMD="$(command -v podman || command -v docker)"
fi

function cleanup() {
    set +e
    # TODO: better container management
    $CMD kill $($CMD ps -a -q --filter ancestor=docker.io/andrewazores/container-jmx-listener-podman)
    $CMD kill $($CMD ps -a -q --filter ancestor=docker.io/andrewazores/container-jmx-listener-docker)
    $CMD kill $($CMD ps -a -q --filter ancestor=docker.io/andrewazores/container-jmx-client)
    $CMD rm $($CMD ps -a -q --filter ancestor=docker.io/andrewazores/container-jmx-listener-podman)
    $CMD rm $($CMD ps -a -q --filter ancestor=docker.io/andrewazores/container-jmx-listener-docker)
    $CMD rm $($CMD ps -a -q --filter ancestor=docker.io/andrewazores/container-jmx-client)

    if [ "$CMD" == "$(command -v podman)" ]; then
        sudo podman pod kill podman-jmx-test
        sudo podman pod rm podman-jmx-test
    fi
}

cleanup
trap cleanup EXIT

if [ "$CMD" == "$(command -v podman)" ]; then
    sudo podman pod create --name podman-jmx-test
else
    docker network create --attachable docker-jmx-test
fi

set -e

RECORDING_DIR="$(pwd)/recordings"
mkdir -p "$RECORDING_DIR"

if [ "$CMD" == "$(command -v podman)" ]; then
    sudo podman run --pod podman-jmx-test --name container-jmx-listener -d docker.io/andrewazores/container-jmx-listener-podman
else
    docker run --rm --net=docker-jmx-test --name container-jmx-listener -p 9090:9090 -d docker.io/andrewazores/container-jmx-listener-docker
fi
echo "Waiting for start"
# TODO: better detection of container startup
sleep 2
pushd build/libs
if [ "$CMD" == "$(command -v podman)" ]; then
    sudo podman run --pod podman-jmx-test --rm -it -u "$(id -u)" -v "$RECORDING_DIR:/recordings" docker.io/andrewazores/container-jmx-client "$@"
else
    docker run --rm --net=docker-jmx-test -it -u "$(id -u)" -v "$RECORDING_DIR:/recordings" docker.io/andrewazores/container-jmx-client "$@"
fi
popd
