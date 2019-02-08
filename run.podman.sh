#!/bin/bash

set -x

function cleanup() {
    set +e
    # TODO: better container management
    podman kill $(podman ps -a -q --filter ancestor=docker.io/andrewazores/container-jmx-listener)
    podman kill $(podman ps -a -q --filter ancestor=docker.io/andrewazores/container-jmx-client)
    podman rm $(podman ps -a -q --filter ancestor=docker.io/andrewazores/container-jmx-listener)
    podman rm $(podman ps -a -q --filter ancestor=docker.io/andrewazores/container-jmx-client)
    podman pod kill jmx-test
    podman pod rm -f jmx-test
}

cleanup
trap cleanup EXIT

set -e

podman pod create --name jmx-test

podman create --pod jmx-test \
    --name jmx-listener --hostname jmx-listener \
    --net=bridge \
    -p 9090:9090 \
    -d docker.io/andrewazores/container-jmx-listener

# TODO fix pod DNS. jmx-client is not currently able to determine its own
# host-accessible hostname/IP, which breaks the ability for the container to
# print messages pointing to its own download locations.
if [ -z $CONTAINER_DOWNLOAD_HOST ]; then
    CONTAINER_DOWNLOAD_HOST=jmx-client
fi

podman create --pod jmx-test \
    --name jmx-client --hostname jmx-client \
    --net=container:jmx-listener \
    -e CONTAINER_DOWNLOAD_HOST=$CONTAINER_DOWNLOAD_HOST \
    -e CONTAINER_DOWNLOAD_PORT=$CONTAINER_DOWNLOAD_PORT \
    --rm -it docker.io/andrewazores/container-jmx-client "$@"

podman pod start jmx-test
podman attach jmx-client