#!/bin/sh

set -x
set -e

./gradlew clean build
docker build -t docker-jmx-test:latest .
