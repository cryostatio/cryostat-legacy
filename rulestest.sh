#!/bin/sh

# FIXME this should be an integration test

# run smoketest.sh first in a separate terminal, then this once the ContainerJFR instance has finished startup

# FIXME once RuleProcessor can apply new rules to existing targets, remove podman commands

set -x
set -e

if [ -z "$CJFR_HOST" ]; then
    CJFR_HOST="https://0.0.0.0:8181"
fi

TARGET_CONTAINER=vertx-fib-demo-1

echo "Killing $TARGET_CONTAINER container"
podman kill $TARGET_CONTAINER

demoAppServiceUrl="service:jmx:rmi:///jndi/rmi://container-jfr:9093/jmxrmi"
demoAppTargetId="$(echo -n $demoAppServiceUrl | jq -sRr @uri)"

sleep 2
echo "POSTing $TARGET_CONTAINER credentials"
curl -k \
    -X POST \
    -F username=admin \
    -F password=adminpass123 \
    -F persist=true \
    "$CJFR_HOST/api/v2/targets/$demoAppTargetId/credentials"

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
    "$CJFR_HOST/api/v2/rules"

sleep 5
echo "GETing the same rule definition"
curl -k \
    -X GET \
    "$CJFR_HOST/api/v2/rules/Default_Rule"

sleep 5
echo "GETing all rule definitions"
curl -k \
    -X GET \
    "$CJFR_HOST/api/v2/rules"

sleep 5
echo "Restarting $TARGET_CONTAINER"
podman run \
    --name $TARGET_CONTAINER \
    --pod container-jfr \
    --env JMX_PORT=9093 \
    --rm -d quay.io/andrewazores/vertx-fib-demo:0.6.0
