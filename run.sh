#!/bin/sh

set -x

function cleanup() {
    set +e
    # TODO: better container management
    docker kill $(docker ps -a -q --filter ancestor=andrewazores/container-jmx-client)
    docker rm $(docker ps -a -q --filter ancestor=andrewazores/container-jmx-client)
    docker kill $(docker ps -a -q --filter ancestor=defreitas/dns-proxy-server)
}

cleanup
trap cleanup EXIT

set +e
docker network create --attachable jmx-test
set -e

docker run \
    -d \
    --hostname dns.mageddo \
    --restart=unless-stopped \
    -p 5380:5380 \
    -v /var/run/docker.sock:/var/run/docker.sock \
    -v /etc/resolv.conf:/etc/resolv.conf \
    defreitas/dns-proxy-server

docker run \
    --net jmx-test \
    --hostname jmx-client \
    --name jmx-client \
    --memory 80M \
    --cpus 1.0 \
    -p 9090:9090 \
    -e CONTAINER_DOWNLOAD_HOST=$CONTAINER_DOWNLOAD_HOST \
    -e CONTAINER_DOWNLOAD_PORT=$CONTAINER_DOWNLOAD_PORT \
    --rm -it andrewazores/container-jmx-client "$@"
