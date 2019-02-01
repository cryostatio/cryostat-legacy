#!/bin/bash

set -x

function cleanup() {
    set +e
    # TODO: better container management
    podman kill $(podman ps -a -q --filter ancestor=docker.io/andrewazores/container-jmx-listener-podman)
    podman kill $(podman ps -a -q --filter ancestor=docker.io/andrewazores/container-jmx-client)
    podman rm $(podman ps -a -q --filter ancestor=docker.io/andrewazores/container-jmx-listener-podman)
    podman rm $(podman ps -a -q --filter ancestor=docker.io/andrewazores/container-jmx-client)
    podman pod kill podman-jmx-test
    podman pod rm podman-jmx-test
}

cleanup
trap cleanup EXIT

set -e

RECORDING_DIR="$(pwd)/recordings"
mkdir -p "$RECORDING_DIR"

podman pod create --name podman-jmx-test

podman run --pod podman-jmx-test --name container-jmx-listener -d docker.io/andrewazores/container-jmx-listener-podman
echo "Waiting for start"
# TODO: better detection of container startup
sleep 2
pushd build/libs
podman run --pod podman-jmx-test --rm -it -v "$RECORDING_DIR:/recordings" docker.io/andrewazores/container-jmx-client "$@"
popd