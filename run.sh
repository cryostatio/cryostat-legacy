#!/bin/bash

set -x

if [ -z "$CMD" ]; then
    CMD="$(command -v podman || command -v docker)"
fi

set -e

if [ "$CMD" == "$(command -v podman)" ]; then
    if [[ $EUID -ne 0 ]]; then
        echo "Podman setup must be run as root"
        exit 1
    fi
    bash ./run.podman.sh "$@"
else
    bash ./run.docker.sh "$@"
fi

