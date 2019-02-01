#!/bin/bash

set -x

function cleanup() {
    set +e
    # TODO: better container management
    podman kill $(podman ps -a -q --filter ancestor=docker.io/andrewazores/container-jmx-listener-podman)
    podman kill $(podman ps -a -q --filter ancestor=docker.io/andrewazores/container-jmx-client)
    podman rm $(podman ps -a -q --filter ancestor=docker.io/andrewazores/container-jmx-listener-podman)
    podman rm $(podman ps -a -q --filter ancestor=docker.io/andrewazores/container-jmx-client)
    podman pod kill jmx-test
    podman pod rm jmx-test
}

cleanup
trap cleanup EXIT

set -e

RECORDING_DIR="$(pwd)/recordings"
mkdir -p "$RECORDING_DIR"

podman pod create --name jmx-test

podman create --pod jmx-test --name jmx-listener -d docker.io/andrewazores/container-jmx-listener-podman
podman create --pod jmx-test --name jmx-client --rm -it -v "$RECORDING_DIR:/recordings" docker.io/andrewazores/container-jmx-client "$@"

podman pod start jmx-test
podman attach jmx-client