#!/bin/bash

set -x
set -e

if [ -z "$CMD" ]; then
    CMD="$(command -v podman || command -v docker)"
fi

./gradlew clean build
$CMD build -f Dockerfile.docker.listener -t andrewazores/container-jmx-docker-listener:latest .
$CMD build -f Dockerfile.podman.listener -t andrewazores/container-jmx-podman-listener:latest .
$CMD build -f Dockerfile.client -t andrewazores/container-jmx-client:latest .
