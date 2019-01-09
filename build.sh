#!/bin/sh

javac Listener.java
docker build -t docker-jmx-test .
