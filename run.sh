#!/bin/sh

set -x

function cleanup() {
    set +e
    # TODO: better container management
    docker kill $(docker ps -a -q --filter ancestor=quay.io/rh-jmc-team/container-jfr)
    docker rm $(docker ps -a -q --filter ancestor=quay.io/rh-jmc-team/container-jfr)
}

cleanup
trap cleanup EXIT

set +e
docker network create --attachable container-jfr
set -e

if [ -z "$CONTAINER_JFR_WEB_HOST" ]; then
    CONTAINER_JFR_WEB_HOST="0.0.0.0" # listens on all interfaces and hostnames for testing purposes
fi

if [ -z "$CONTAINER_JFR_WEB_PORT" ]; then
    CONTAINER_JFR_WEB_PORT=8181
fi

if [ -z "$CONTAINER_JFR_EXT_WEB_PORT" ]; then
    CONTAINER_JFR_EXT_WEB_PORT="$CONTAINER_JFR_WEB_PORT"
fi

if [ -z "$CONTAINER_JFR_LISTEN_HOST" ]; then
    CONTAINER_JFR_LISTEN_HOST="$CONTAINER_JFR_WEB_HOST"
fi

if [ -z "$CONTAINER_JFR_LISTEN_PORT" ]; then
    CONTAINER_JFR_LISTEN_PORT=9090;
fi

if [ -z "$CONTAINER_JFR_EXT_LISTEN_PORT" ]; then
    CONTAINER_JFR_EXT_LISTEN_PORT="$CONTAINER_JFR_LISTEN_PORT"
fi

docker run \
    --net container-jfr \
    --hostname container-jfr \
    --name container-jfr \
    --memory 80M \
    --cpus 1.0 \
    --mount source=flightrecordings,target=/flightrecordings \
    -p $CONTAINER_JFR_EXT_LISTEN_PORT:$CONTAINER_JFR_LISTEN_PORT \
    -p $CONTAINER_JFR_EXT_WEB_PORT:$CONTAINER_JFR_WEB_PORT \
    -e CONTAINER_JFR_WEB_HOST=$CONTAINER_JFR_WEB_HOST \
    -e CONTAINER_JFR_WEB_PORT=$CONTAINER_JFR_WEB_PORT \
    -e CONTAINER_JFR_EXT_WEB_PORT=$CONTAINER_JFR_EXT_WEB_PORT \
    -e CONTAINER_JFR_LISTEN_HOST=$CONTAINER_JFR_LISTEN_HOST \
    -e CONTAINER_JFR_LISTEN_PORT=$CONTAINER_JFR_LISTEN_PORT \
    -e CONTAINER_JFR_EXT_LISTEN_PORT=$CONTAINER_JFR_EXT_LISTEN_PORT \
    -e GRAFANA_DATASOURCE_URL=$GRAFANA_DATASOURCE_URL \
    -e GRAFANA_DASHBOARD_URL=$GRAFANA_DASHBOARD_URL \
    -e USE_LOW_MEM_PRESSURE_STREAMING=$USE_LOW_MEM_PRESSURE_STREAMING \
    --rm -it quay.io/rh-jmc-team/container-jfr "$@"
