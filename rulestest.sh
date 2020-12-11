#!/bin/sh

# FIXME this should be an integration test

demoAppServiceUrl="service:jmx:rmi:///jndi/rmi://container-jfr:9093/jmxrmi"

curl -vLk \
    -X POST \
    -F username=admin \
    -F password=adminpass123 \
    -F persist=true \
    "https://0.0.0.0:8181/api/v2/credentials/$(echo -n $demoAppServiceUrl | jq -sRr @uri)"

curl -vLk \
    -X POST \
    -F name="Default Rule" \
    -F targetAlias="es.andrewazor.demo.Main" \
    -F description="This is a test rule" \
    -F eventSpecifier="template=Continuous,type=TARGET" \
    -F archivalPeriodSeconds="60" \
    -F preservedArchives="3" \
    https://0.0.0.0:8181/api/v2/rules
