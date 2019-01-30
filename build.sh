#!/bin/bash

set -x
set -e

if [ -z "$CMD" ]; then
    CMD="$(command -v podman || command -v docker)"
fi

./gradlew clean build
$CMD build -f Dockerfile.podman.listener -t docker.io/andrewazores/docker-jmx-listener-podman:latest .
$CMD build -f Dockerfile.docker.listener -t docker.io/andrewazores/docker-jmx-listener-docker:latest .
$CMD build -f Dockerfile.client -t docker.io/andrewazores/docker-jmx-client:latest .
