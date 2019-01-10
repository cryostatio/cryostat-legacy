#!/bin/sh

set -e

./gradlew clean build
docker build -t docker-jmx-test .
