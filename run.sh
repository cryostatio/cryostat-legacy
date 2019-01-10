#!/bin/sh

# TODO: better Docker container management
docker kill $(docker ps -a -q --filter ancestor=docker-test)
docker rm $(docker ps -a -q --filter ancestor=docker-test)

set -e

docker run -d -p 9090:9090 -p 9091:9091 docker-test
echo "Waiting for start"
sleep 5
pushd build/classes/java/main
java JMXClient
popd
