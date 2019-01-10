#!/bin/sh

set -e

docker run -d -p 9090:9090 -p 9091:9091 docker-test
echo "Waiting for start"
sleep 5
java JMXClient
