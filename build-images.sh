#!/bin/sh

set -x
set -e

CONTAINER_JFR_DEBUG=true ./gradlew jibDockerBuild

CONTAINER_JFR_MINIMAL=true ./gradlew jibDockerBuild

CONTAINER_JFR_DEBUG=true CONTAINER_JFR_MINIMAL=true ./gradlew jibDockerBuild

./gradlew jibDockerBuild
