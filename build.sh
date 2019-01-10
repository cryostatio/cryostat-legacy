#!/bin/sh

set -e

javac Listener.java
javac JMXClient.java
docker build -t docker-jmx-test .
