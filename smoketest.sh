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

function runDemoApps() {
    podman run \
        --name vertx-fib-demo-1 \
        --env HTTP_PORT=8081 \
        --env JMX_PORT=9093 \
        --pod cryostat \
        --rm -d quay.io/andrewazores/vertx-fib-demo:0.7.0

    podman run \
        --name vertx-fib-demo-2 \
        --env HTTP_PORT=8081 \
        --env JMX_PORT=9094 \
        --env USE_AUTH=true \
        --pod cryostat \
        --rm -d quay.io/andrewazores/vertx-fib-demo:0.7.0

    podman run \
        --name vertx-fib-demo-3 \
        --env HTTP_PORT=8081 \
        --env JMX_PORT=9095 \
        --env USE_SSL=true \
        --env USE_AUTH=true \
        --pod cryostat \
        --rm -d quay.io/andrewazores/vertx-fib-demo:0.7.0

    podman run \
        --name quarkus-test \
        --pod cryostat \
        --rm -d quay.io/andrewazores/quarkus-test:0.0.2

    # copy a jboss-client.jar into /clientlib first
    # manual entry URL: service:jmx:remote+http://localhost:9990
    podman run \
        --name wildfly \
        --pod cryostat \
        --rm -d quay.io/andrewazores/wildfly-demo:v0.0.1
}

function runJfrDatasource() {
    podman run \
        --name jfr-datasource \
        --pod cryostat \
        --rm -d quay.io/cryostat/jfr-datasource:1.0.0
}

function runGrafana() {
    podman run \
        --name grafana \
        --pod cryostat \
        --env GF_INSTALL_PLUGINS=grafana-simple-json-datasource \
        --env GF_AUTH_ANONYMOUS_ENABLED=true \
        --env JFR_DATASOURCE_URL="http://0.0.0.0:8080" \
        --rm -d quay.io/cryostat/cryostat-grafana-dashboard:1.0.0
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
        --publish 9093:9093 \
        --publish 9094:9094 \
        --publish 9095:9095 \
        --publish 9096:9096 \
        --publish 9999:9999 \
        --publish 8082:8082 \
        --publish 9990:9990 \
        --publish 9991:9991
    # 9091: Cryostat RJMX
    # 8181: Cryostat web services
    # 8080: jfr-datasource
    # 3000: grafana
    # 8081: vertx-fib-demo
    # 9093: vertx-fib-demo-1 RJMX
    # 9094: vertx-fib-demo-2 RJMX
    # 9095: vertx-fib-demo-3 RJMX
    # 9096: quarkus-test RJMX
    # 9999: quarkus-test HTTP
    # 8082: Wildfly HTTP
    # 9990: Wildfly Admin Console
    # 9990: Wildfly RJMX
}

function destroyPod() {
    podman pod kill cryostat
    podman pod rm cryostat
}
trap destroyPod EXIT

createPod
runDemoApps
runJfrDatasource
runGrafana
runCryostat
