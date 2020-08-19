#!/bin/sh

set -x
set -e

function runContainerJFR() {
    local DIR="$(dirname "$(readlink -f "$0")")"
    GRAFANA_DATASOURCE_URL="http://0.0.0.0:8080" GRAFANA_DASHBOARD_URL="http://0.0.0.0:3000" sh "$DIR/run.sh"
}

function runDemoApp() {
    podman run \
        --name vertx-fib-demo \
        -p 8080:8081 \
        -p 9093:9093 \
        --pod container-jfr \
        --rm -d quay.io/andrewazores/vertx-fib-demo:0.2.0
}

function runJfrDatasource() {
    podman run \
        --name jfr-datasource \
        -p 8080:8080 \
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

function configureGrafanaDashboard() {
    while ! curl "http://0.0.0.0:3000/api/health"; do
        sleep 5
    done
    local TEMP="$(mktemp -d)"
    pushd "$TEMP"
    echo '{"overwrite":false,"dashboard":' > dashboard.json
    curl https://raw.githubusercontent.com/rh-jmc-team/jfr-datasource/master/dashboards/dashboard.json >> dashboard.json
    echo "}" >> dashboard.json
    sed -i 's/"id": 1,/"id": null,/' dashboard.json
    curl -X POST -H "Content-Type: application/json" http://admin:admin@0.0.0.0:3000/api/dashboards/db -T - < dashboard.json
    popd
}

function runGrafana() {
    podman run \
        --name grafana \
        -p 3000:3000 \
        --pod container-jfr \
        --env GF_INSTALL_PLUGINS=grafana-simple-json-datasource \
        --env GF_AUTH_ANONYMOUS_ENABLED=true \
        --rm -d docker.io/grafana/grafana:6.4.4
    configureGrafanaDatasource
    configureGrafanaDashboard
}

function cleanup() {
    if podman container exists vertx-fib-demo; then
        podman kill vertx-fib-demo
    fi
    if podman container exists jfr-datasource; then
        podman kill jfr-datasource
    fi
    if podman pod exists container-jfr; then
        podman pod kill container-jfr
        podman pod rm container-jfr
    fi
}
trap cleanup EXIT
cleanup

if ! podman pod exists container-jfr; then
    podman pod create \
        --hostname container-jfr \
        --name container-jfr \
        --publish 8181 \
        --publish 3000
fi

runDemoApp
runJfrDatasource
runGrafana
runContainerJFR
