#!/bin/sh

set -x
set -e

function runCryostat() {
    local DIR="$(dirname "$(readlink -f "$0")")"
    local host="$(xpath -q -e 'project/properties/cryostat.itest.webHost/text()' pom.xml)"
    local datasourcePort="$(xpath -q -e 'project/properties/cryostat.itest.jfr-datasource.port/text()' pom.xml)"
    local grafanaPort="$(xpath -q -e 'project/properties/cryostat.itest.grafana.port/text()' pom.xml)"
    GRAFANA_DATASOURCE_URL="http://${host}:${datasourcePort}" \
        GRAFANA_DASHBOARD_URL="http://${host}:${grafanaPort}" \
        CRYOSTAT_RJMX_USER=smoketest \
        CRYOSTAT_RJMX_PASS=smoketest \
        CRYOSTAT_ALLOW_UNTRUSTED_SSL=true \
        CRYOSTAT_REPORT_GENERATOR="http://${host}:10001" \
        exec "$DIR/run.sh"
}

function runDemoApps() {
    podman run \
        --name vertx-fib-demo-1 \
        --env HTTP_PORT=8081 \
        --env JMX_PORT=9093 \
        --pod cryostat-pod \
        --rm -d quay.io/andrewazores/vertx-fib-demo:0.7.0

    podman run \
        --name vertx-fib-demo-2 \
        --env HTTP_PORT=8081 \
        --env JMX_PORT=9094 \
        --env USE_AUTH=true \
        --pod cryostat-pod \
        --rm -d quay.io/andrewazores/vertx-fib-demo:0.7.0

    podman run \
        --name vertx-fib-demo-3 \
        --env HTTP_PORT=8081 \
        --env JMX_PORT=9095 \
        --env USE_SSL=true \
        --env USE_AUTH=true \
        --pod cryostat-pod \
        --rm -d quay.io/andrewazores/vertx-fib-demo:0.7.0

    podman run \
        --name quarkus-test \
        --pod cryostat-pod \
        --rm -d quay.io/andrewazores/quarkus-test:0.0.2

    # copy a jboss-client.jar into /clientlib first
    # manual entry URL: service:jmx:remote+http://localhost:9990
    podman run \
        --name wildfly \
        --pod cryostat-pod \
        --rm -d quay.io/andrewazores/wildfly-demo:v0.0.1
}

function runJfrDatasource() {
    local stream="$(xpath -q -e 'project/properties/cryostat.itest.jfr-datasource.imageStream/text()' pom.xml)"
    local tag="$(xpath -q -e 'project/properties/cryostat.itest.jfr-datasource.version/text()' pom.xml)"
    podman run \
        --name jfr-datasource \
        --pull always \
        --pod cryostat-pod \
        --rm -d "${stream}:${tag}"
}

function runGrafana() {
    local stream="$(xpath -q -e 'project/properties/cryostat.itest.grafana.imageStream/text()' pom.xml)"
    local tag="$(xpath -q -e 'project/properties/cryostat.itest.grafana.version/text()' pom.xml)"
    local host="$(xpath -q -e 'project/properties/cryostat.itest.webHost/text()' pom.xml)"
    local port="$(xpath -q -e 'project/properties/cryostat.itest.jfr-datasource.port/text()' pom.xml)"
    podman run \
        --name grafana \
        --pull always \
        --pod cryostat-pod \
        --env GF_INSTALL_PLUGINS=grafana-simple-json-datasource \
        --env GF_AUTH_ANONYMOUS_ENABLED=true \
        --env JFR_DATASOURCE_URL="http://${host}:${port}" \
        --rm -d "${stream}:${tag}"
}

function runReportGenerator() {
    local RJMX_PORT=10000
    podman run \
        --name reports \
        --pull always \
        --pod cryostat-pod \
        --cpus 1 \
        --memory 512M \
        --restart on-failure \
        --env JAVA_OPTIONS="-XX:ActiveProcessorCount=1 -XX:+UseSerialGC -Dorg.openjdk.jmc.flightrecorder.parser.singlethreaded=true -Dcom.sun.management.jmxremote.autodiscovery=true -Dcom.sun.management.jmxremote.port=${RJMX_PORT} -Dcom.sun.management.jmxremote.rmi.port=${RJMX_PORT} -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false" \
        --env QUARKUS_HTTP_PORT=10001 \
        --rm -d quay.io/cryostat/cryostat-reports:latest
}

function createPod() {
    local jmxPort="$(xpath -q -e 'project/properties/cryostat.rjmxPort/text()' pom.xml)"
    local webPort="$(xpath -q -e 'project/properties/cryostat.webPort/text()' pom.xml)"
    local datasourcePort="$(xpath -q -e 'project/properties/cryostat.itest.jfr-datasource.port/text()' pom.xml)"
    local grafanaPort="$(xpath -q -e 'project/properties/cryostat.itest.grafana.port/text()' pom.xml)"
    podman pod create \
        --replace \
        --hostname cryostat \
        --name cryostat-pod \
        --publish "${jmxPort}:${jmxPort}" \
        --publish "${webPort}:${webPort}" \
        --publish "${datasourcePort}:${datasourcePort}" \
        --publish "${grafanaPort}:${grafanaPort}" \
        --publish 8081:8081 \
        --publish 9093:9093 \
        --publish 9094:9094 \
        --publish 9095:9095 \
        --publish 9096:9096 \
        --publish 9999:9999 \
        --publish 8082:8082 \
        --publish 9990:9990 \
        --publish 9991:9991 \
        --publish 10000:10000 \
        --publish 10001:10001
    # 8081: vertx-fib-demo
    # 9093: vertx-fib-demo-1 RJMX
    # 9094: vertx-fib-demo-2 RJMX
    # 9095: vertx-fib-demo-3 RJMX
    # 9096: quarkus-test RJMX
    # 9999: quarkus-test HTTP
    # 8082: Wildfly HTTP
    # 9990: Wildfly Admin Console
    # 9991: Wildfly RJMX
    # 10000: cryostat-reports RJMX
    # 10001: cryostat-reports HTTP
}

function destroyPod() {
    podman pod kill cryostat-pod
    podman pod rm cryostat-pod
}
trap destroyPod EXIT

createPod
runDemoApps
runJfrDatasource
runGrafana
runReportGenerator
runCryostat
