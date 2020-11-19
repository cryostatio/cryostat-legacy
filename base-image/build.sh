#!/bin/sh

if [ -z "$IMAGE" ]; then
    IMAGE="quay.io/rh-jmc-team/container-jfr-base"
fi

if [ -z "$TAG" ]; then
    TAG="0.3.0"
fi

if [ -z "$BUILDER" ]; then
    BUILDER="podman"
fi

$BUILDER build -t $IMAGE:$TAG -f "$(dirname $0)"/Containerfile
$BUILDER tag $IMAGE:$TAG $IMAGE:latest
