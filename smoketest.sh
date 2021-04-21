#!/bin/sh

set -x
set -e

function runCryostat() {
    local DIR="$(dirname "$(readlink -f "$0")")"
    GRAFANA_DATASOURCE_URL="http://0.0.0.0:8080" \
        GRAFANA_DASHBOARD_URL="http://0.0.0.0:3000" \
        CRYOSTAT_RJMX_USER=smoketest \
        CRYOSTAT_RJMX_PASS=smoketest \
        exec "$DIR/run.sh"
}

function runDemoApp() {
    podman run \
        --name vertx-fib-demo \
        --pod cryostat \
        --rm -d quay.io/andrewazores/vertx-fib-demo:0.4.0
}

function runJfrDatasource() {
    podman run \
        --name jfr-datasource \
        --pod cryostat \
        --rm -d quay.io/cryostat/jfr-datasource:0.0.1
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

function configureGrafanaDashboard() {
    while ! curl "http://0.0.0.0:3000/api/health"; do
        sleep 5
    done
    local TEMP="$(mktemp -d)"
    pushd "$TEMP"
    echo '{"overwrite":false,"dashboard":' > dashboard.json
    curl https://raw.githubusercontent.com/cryostat/jfr-datasource/master/dashboards/dashboard.json >> dashboard.json
    echo "}" >> dashboard.json
    sed -i 's/"id": 1,/"id": null,/' dashboard.json
    curl -X POST -H "Content-Type: application/json" http://admin:admin@0.0.0.0:3000/api/dashboards/db -T - < dashboard.json
    popd
}

function runGrafana() {
    podman run \
        --name grafana \
        --pod cryostat \
        --env GF_INSTALL_PLUGINS=grafana-simple-json-datasource \
        --env GF_AUTH_ANONYMOUS_ENABLED=true \
        --rm -d docker.io/grafana/grafana:6.4.4
    configureGrafanaDatasource
    configureGrafanaDashboard
}

function createPod() {
    podman pod create \
        --replace \
        --hostname cryostat \
        --name cryostat \
        --publish 9091:9091 \
        --publish 8181:8181 \
        --publish 8080:8080 \
        --publish 3000:3000 \
        --publish 8081:8081 \
        --publish 9093:9093
    # 9091: Cryostat RJMX
    # 8181: Cryostat web services
    # 8080: jfr-datasource
    # 3000: grafana
    # 8081: vertx-fib-demo
    # 9093: vertx-fib-demo RJMX
}

function destroyPod() {
    podman pod kill cryostat
    podman pod rm cryostat
}
trap destroyPod EXIT

createPod
runDemoApp
runJfrDatasource
runGrafana
runCryostat
