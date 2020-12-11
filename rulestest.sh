#!/bin/sh

# TODO delete me, this should be an integration test

curl -vLk \
    -X POST \
    -H "X-JMX-Authorization: Basic $(echo -n admin:adminpass123 | base64)" \
    -F username=smoketest \
    -F password=smoketest \
    -F persist=true \
    https://0.0.0.0:8181/api/v2/targets/localhost%3A9093/credentials

sleep 5

curl -vLk \
    -X DELETE \
    -H "X-JMX-Authorization: Basic $(echo -n admin:adminpass123 | base64)" \
    https://0.0.0.0:8181/api/v2/targets/localhost%3A9093/credentials
