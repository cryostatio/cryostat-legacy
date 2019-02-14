#!/bin/bash

set -x
set -e

if [ -z "$CMD" ]; then
    CMD="$(command -v podman || command -v docker)"
fi

./gradlew clean build
$CMD build -f Dockerfile.listener -t andrewazores/container-jmx-listener:latest .
$CMD build -f Dockerfile.client -t andrewazores/container-jmx-client:latest .
