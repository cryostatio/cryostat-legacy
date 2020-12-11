#!/bin/sh

# TODO delete me, this should be an integration test

curl -vLk \
    -X POST \
    -H "X-JMX-Authorization: Basic $(echo -n admin:adminpass123 | base64)" \
    -F username=admin \
    -F password=adminpass123 \
    -F persist=true \
    https://0.0.0.0:8181/api/v2/targets/localhost%3A9093/credentials

curl -vLk \
    -X POST \
    -F name="Default Rule" \
    -F targetAlias="es.andrewazor.demo.Main" \
    -F description="This is a test rule" \
    -F eventSpecifier="template=Continuous,type=TARGET" \
    -F archivalPeriodSeconds="60" \
    -F preservedArchives="3" \
    https://0.0.0.0:8181/api/v2/rules
