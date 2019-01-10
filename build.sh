#!/bin/sh

set -e

./gradlew build
docker build -t docker-jmx-test .
