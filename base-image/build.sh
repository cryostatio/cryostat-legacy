#!/bin/sh

if [ -z "$TAG" ]; then
    TAG="0.1.0"
fi

if [ -z "$BUILDER" ]; then
    BUILDER="podman"
fi

IMAGE="quay.io/rh-jmc-team/container-jfr-base"

$BUILDER build -t $IMAGE:$TAG -f "$(dirname $0)"/Containerfile
$BUILDER tag $IMAGE:$TAG $IMAGE:latest
