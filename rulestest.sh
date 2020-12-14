#!/bin/sh

# FIXME this should be an integration test

# run smoketest.sh first in a separate terminal, then this once the ContainerJFR instance has finished startup

# FIXME once RuleProcessor can apply new rules to existing targets, remove podman commands

set -x
set -e

echo "Killing vertx-fib-demo container"
podman kill vertx-fib-demo

demoAppServiceUrl="service:jmx:rmi:///jndi/rmi://container-jfr:9093/jmxrmi"
demoAppTargetId="$(echo -n $demoAppServiceUrl | jq -sRr @uri)"

sleep 2
echo "POSTing vertx-fib-demo credentials"
curl -k \
    -X POST \
    -F username=admin \
    -F password=adminpass123 \
    -F persist=true \
    "https://0.0.0.0:8181/api/v2/targets/$demoAppTargetId/credentials"

sleep 5
echo "POSTing a rule definition"
curl -k \
    -X POST \
    -F name="Default Rule" \
    -F targetAlias="es.andrewazor.demo.Main" \
    -F description="This is a test rule" \
    -F eventSpecifier="template=Continuous,type=TARGET" \
    -F archivalPeriodSeconds="60" \
    -F preservedArchives="3" \
    https://0.0.0.0:8181/api/v2/rules

sleep 5
echo "GETing the same rule definition"
curl -k \
    -X GET \
    https://0.0.0.0:8181/api/v2/rules/Default_Rule

sleep 5
echo "GETing all rule definitions"
curl -k \
    -X GET \
    https://0.0.0.0:8181/api/v2/rules

sleep 5
echo "Restarting vertx-fib-demo"
podman run \
    --name vertx-fib-demo \
    --pod container-jfr \
    --rm -d quay.io/andrewazores/vertx-fib-demo:0.4.0
