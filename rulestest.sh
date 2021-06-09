#!/bin/sh

# FIXME remove this, this is a manual testing version of AutoRulesIT.java

# run smoketest.sh first in a separate terminal, then this once the Cryostat instance has finished startup

# FIXME once RuleProcessor can apply new rules to existing targets, remove podman commands

set -x
set -e

if [ -z "$CRYOSTAT_HOST" ]; then
    CRYOSTAT_HOST="https://0.0.0.0:8181"
fi

TARGET_CONTAINER=vertx-fib-demo-2

echo "Killing $TARGET_CONTAINER container"
podman kill $TARGET_CONTAINER

demoAppServiceUrl="service:jmx:rmi:///jndi/rmi://cryostat:9093/jmxrmi"
demoAppTargetId="$(echo -n $demoAppServiceUrl | jq -sRr @uri)"

sleep 2
echo "POSTing $TARGET_CONTAINER credentials"
curl -k \
    -X POST \
    -F username=admin \
    -F password=adminpass123 \
    "$CRYOSTAT_HOST/api/v2/targets/$demoAppTargetId/credentials"

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
    "$CRYOSTAT_HOST/api/v2/rules"

sleep 5
echo "GETing the same rule definition"
curl -k \
    -X GET \
    "$CRYOSTAT_HOST/api/v2/rules/Default_Rule"

sleep 5
echo "GETing all rule definitions"
curl -k \
    -X GET \
    "$CRYOSTAT_HOST/api/v2/rules"

sleep 5
echo "Restarting $TARGET_CONTAINER"
podman run \
    --name $TARGET_CONTAINER \
    --pod cryostat \
    --env HTTP_PORT=8081 \
    --env JMX_PORT=9094 \
    --rm -d quay.io/andrewazores/vertx-fib-demo:0.7.0

sleep 5
echo "Deleting rule definition"
curl -k \
    -X DELETE \
    "$CRYOSTAT_HOST/api/v2/rules/Default_Rule"
