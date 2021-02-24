#!/bin/sh

set -x
set -e

function runContainerJFR() {
    local DIR="$(dirname "$(readlink -f "$0")")"
    GRAFANA_DATASOURCE_URL="http://0.0.0.0:8080" \
        GRAFANA_DASHBOARD_URL="http://0.0.0.0:3000" \
        CONTAINER_JFR_RJMX_USER=smoketest \
        CONTAINER_JFR_RJMX_PASS=smoketest \
        exec "$DIR/run.sh"
}

function runDemoApps() {
    podman run \
        --name vertx-fib-demo-1 \
        --env JMX_PORT=9093 \
        --pod container-jfr \
        --rm -d quay.io/andrewazores/vertx-fib-demo:0.6.0

    podman run \
        --name vertx-fib-demo-2 \
        --env JMX_PORT=9094 \
        --env USE_AUTH=true \
        --pod container-jfr \
        --rm -d quay.io/andrewazores/vertx-fib-demo:0.6.0

    podman run \
        --name vertx-fib-demo-3 \
        --env JMX_PORT=9095 \
        --env USE_SSL=true \
        --env USE_AUTH=true \
        --pod container-jfr \
        --rm -d quay.io/andrewazores/vertx-fib-demo:0.6.0

    podman run \
        --name quarkus-test \
        --pod container-jfr \
        --rm -d quay.io/andrewazores/quarkus-test:0.0.2
}

function runJfrDatasource() {
    podman run \
        --name jfr-datasource \
        --pod container-jfr \
        --rm -d quay.io/rh-jmc-team/jfr-datasource:0.0.1
}

function configureGrafanaDatasource() {
    while ! curl "http://0.0.0.0:3000/api/health"; do
        sleep 5
    done
    local TEMP="$(mktemp -d)"
    pushd "$TEMP"

    echo "{" > datasource.json
    echo '"name":"jfr-datasource",' >> datasource.json
    echo '"type":"grafana-simple-json-datasource",' >> datasource.json
    echo '"url":"http://0.0.0.0:8080",' >> datasource.json
    echo '"access":"proxy",' >> datasource.json
    echo '"basicAuth":false,' >> datasource.json
    echo '"isDefault":true' >> datasource.json
    echo "}" >> datasource.json

    curl -X POST -H "Content-Type: application/json" "http://admin:admin@0.0.0.0:3000/api/datasources" -T - < datasource.json
    popd
}

function runGrafana() {
    podman run \
        --name grafana \
        --pod container-jfr \
        --env GF_INSTALL_PLUGINS=grafana-simple-json-datasource \
        --env GF_AUTH_ANONYMOUS_ENABLED=true \
        --rm -d quay.io/rh-jmc-team/container-jfr-grafana-dashboard:0.1.0
    configureGrafanaDatasource
}

function createPod() {
    podman pod create \
        --replace \
        --hostname container-jfr \
        --name container-jfr \
        --publish 9091:9091 \
        --publish 8181:8181 \
        --publish 8080:8080 \
        --publish 3000:3000 \
        --publish 8081:8081 \
        --publish 9093:9093 \
        --publish 9094:9094 \
        --publish 9095:9095 \
        --publish 9096:9096 \
        --publish 9999:9999
    # 9091: ContainerJFR RJMX
    # 8181: ContainerJFR web services
    # 8080: jfr-datasource
    # 3000: grafana
    # 8081: vertx-fib-demo
    # 9093: vertx-fib-demo-1 RJMX
    # 9094: vertx-fib-demo-2 RJMX
    # 9095: vertx-fib-demo-3 RJMX
    # 9096: quarkus-test RJMX
    # 9999: quarkus-test HTTP
}

function destroyPod() {
    podman pod kill container-jfr
    podman pod rm container-jfr
}
trap destroyPod EXIT

createPod
runDemoApps
runJfrDatasource
runGrafana
runContainerJFR
