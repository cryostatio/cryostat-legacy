#!/bin/sh

set -x
set -e

./gradlew clean build
docker build -f Dockerfile.listener -t docker-jmx-listener:latest .
docker build -f Dockerfile.client -t docker-jmx-client:latest .
