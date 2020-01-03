#!/bin/sh

set -x
set -e

CONTAINER_JFR_MINIMAL=true ./gradlew jibDockerBuild

./gradlew jibDockerBuild
