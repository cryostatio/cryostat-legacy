#!/bin/sh

set -x
set -e

function runCryostat() {
    local DIR="$(dirname "$(readlink -f "$0")")"
    local host="$(xpath -q -e 'project/properties/cryostat.itest.webHost/text()' pom.xml)"
    local datasourcePort="$(xpath -q -e 'project/properties/cryostat.itest.jfr-datasource.port/text()' pom.xml)"
    local grafanaPort="$(xpath -q -e 'project/properties/cryostat.itest.grafana.port/text()' pom.xml)"
    # credentials `user:pass`
    echo "user:d74ff0ee8da3b9806b18c877dbf29bbde50b5bd8e4dad7a3a725000feb82e8f1" > "./conf/cryostat-users.properties"

    if [ "$1" = "postgres" ]; then
        JDBC_URL="jdbc:postgresql://cryostat:5432/cryostat"
        JDBC_DRIVER="org.postgresql.Driver"
        HIBERNATE_DIALECT="org.hibernate.dialect.PostgreSQL95Dialect"
        JDBC_USERNAME="postgres"
        JDBC_PASSWORD="abcd1234"
        HBM2DDL="update"
    elif [ "$1" = "h2file" ]; then
        JDBC_URL="jdbc:h2:file:/opt/cryostat.d/conf.d/h2;INIT=create domain if not exists jsonb as varchar"
        HBM2DDL="update"
    fi

    if [ -z "$CRYOSTAT_AUTH_MANAGER" ]; then
        CRYOSTAT_AUTH_MANAGER="io.cryostat.net.BasicAuthManager"
    fi

    GRAFANA_DATASOURCE_URL="http://${host}:${datasourcePort}" \
        GRAFANA_DASHBOARD_URL="http://${host}:${grafanaPort}" \
        CRYOSTAT_RJMX_USER=smoketest \
        CRYOSTAT_RJMX_PASS=smoketest \
        CRYOSTAT_ALLOW_UNTRUSTED_SSL=true \
        CRYOSTAT_REPORT_GENERATOR="http://${host}:10001" \
        CRYOSTAT_AUTH_MANAGER="$CRYOSTAT_AUTH_MANAGER" \
        CRYOSTAT_JDBC_URL="$JDBC_URL" \
        CRYOSTAT_JDBC_DRIVER="$JDBC_DRIVER" \
        CRYOSTAT_HIBERNATE_DIALECT="$HIBERNATE_DIALECT" \
        CRYOSTAT_JDBC_USERNAME="$JDBC_USERNAME" \
        CRYOSTAT_JDBC_PASSWORD="$JDBC_PASSWORD" \
        CRYOSTAT_HBM2DDL="$HBM2DDL" \
        CRYOSTAT_DEV_MODE="true" \
        exec "$DIR/run.sh"
}

function runPostgres() {
    if [ ! -d "$(dirname $0)/conf/postgres" ]; then
        mkdir "$(dirname $0)/conf/postgres"
    fi
    local image="$(xpath -q -e 'project/properties/postgres.image/text()' pom.xml)"
    local version="$(xpath -q -e 'project/properties/postgres.version/text()' pom.xml)"
    podman run \
        --name postgres \
        --pod cryostat-pod \
        --env POSTGRES_USER=postgres \
        --env POSTGRES_PASSWORD=abcd1234 \
        --env POSTGRES_DB=cryostat \
        --env PGPASSWORD=abcd1234 \
        --mount type=bind,source="$(dirname $0)/conf/postgres",destination=/var/lib/postgresql/data/pgdata,relabel=shared \
        --mount type=bind,source="$(dirname $0)/src/test/resources/postgres",destination=/docker-entrypoint-initdb.d,relabel=shared \
        --env PGDATA=/var/lib/postgresql/data/pgdata \
        --rm -d "${image}:${version}"
}

function runDemoApps() {
    podman run \
        --name vertx-fib-demo-1 \
        --env HTTP_PORT=8081 \
        --env JMX_PORT=9093 \
        --pod cryostat-pod \
        --rm -d quay.io/andrewazores/vertx-fib-demo:0.8.0

    podman run \
        --name vertx-fib-demo-2 \
        --env HTTP_PORT=8082 \
        --env JMX_PORT=9094 \
        --env USE_AUTH=true \
        --pod cryostat-pod \
        --rm -d quay.io/andrewazores/vertx-fib-demo:0.8.0

    podman run \
        --name vertx-fib-demo-3 \
        --env HTTP_PORT=8083 \
        --env JMX_PORT=9095 \
        --env USE_SSL=true \
        --env USE_AUTH=true \
        --pod cryostat-pod \
        --rm -d quay.io/andrewazores/vertx-fib-demo:0.8.0

    podman run \
        --name quarkus-test \
        --pod cryostat-pod \
        --rm -d quay.io/andrewazores/quarkus-test:0.0.2

    if [ -z "$CRYOSTAT_WEB_PORT" ]; then
        local webPort="$(xpath -q -e 'project/properties/cryostat.itest.webPort/text()' pom.xml)"
    else
        local webPort="${CRYOSTAT_WEB_PORT}"
    fi
    if [ -z "$CRYOSTAT_DISABLE_SSL" ]; then
        local protocol="https"
    else
        local protocol="http"
    fi
    podman run \
        --name quarkus-test-plugin \
        --pod cryostat-pod \
        --restart unless-stopped \
        --env QUARKUS_HTTP_PORT=10010 \
        --env ORG_ACME_CRYOSTATSERVICE_AUTHORIZATION="Basic $(echo -n user:pass | base64)" \
        --env ORG_ACME_CRYOSTATSERVICE_MP_REST_URL="${protocol}://cryostat:${webPort}" \
        --env ORG_ACME_CRYOSTATSERVICE_CALLBACK_HOST="cryostat" \
        --env ORG_ACME_JMXHOST="cryostat" \
        --env ORG_ACME_JMXPORT="9097" \
        -d quay.io/andrewazores/quarkus-test:0.0.8

    podman run \
        --name quarkus-test-plugin-2 \
        --pod cryostat-pod \
        --restart unless-stopped \
        --env QUARKUS_HTTP_PORT=10011 \
        --env JAVA_OPTIONS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager -Dcom.sun.management.jmxremote.port=9197 -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.authenticate=false" \
        --env ORG_ACME_CRYOSTATSERVICE_AUTHORIZATION="Basic $(echo -n user:pass | base64)" \
        --env ORG_ACME_CRYOSTATSERVICE_MP_REST_URL="${protocol}://cryostat:${webPort}" \
        --env ORG_ACME_CRYOSTATSERVICE_CALLBACK_HOST="cryostat" \
        --env ORG_ACME_JMXHOST="cryostat" \
        --env ORG_ACME_JMXPORT="9197" \
        -d quay.io/andrewazores/quarkus-test:0.0.8

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
    if [ -z "$CRYOSTAT_WEB_PORT" ]; then
        local webPort="$(xpath -q -e 'project/properties/cryostat.itest.webPort/text()' pom.xml)"
    else
        local webPort="${CRYOSTAT_WEB_PORT}"
    fi
    if [ -z "$CRYOSTAT_DISABLE_SSL" ]; then
        local protocol="https"
    else
        local protocol="http"
    fi
    local RJMX_PORT=7777
    podman run \
        --name reports \
        --pod cryostat-pod \
        --cpus 1 \
        --memory 512M \
        --restart on-failure \
        --env JAVA_OPTIONS="-XX:ActiveProcessorCount=1 -XX:+UseSerialGC -Dorg.openjdk.jmc.flightrecorder.parser.singlethreaded=true -javaagent:/deployments/app/cryostat-agent.jar" \
        --env QUARKUS_HTTP_PORT=10001 \
        --env CRYOSTAT_AGENT_APP_NAME="cryostat-reports" \
        --env CRYOSTAT_AGENT_CALLBACK="http://localhost:9977/" \
        --env CRYOSTAT_AGENT_BASEURI="${protocol}://cryostat:${webPort}/" \
        --env CRYOSTAT_AGENT_AUTHORIZATION="Basic $(echo -n user:pass | base64)" \
        --env CRYOSTAT_AGENT_TRUST_ALL="true" \
        --env CRYOSTAT_AGENT_HARVESTER_PERIOD_MS=60000 \
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
        --publish 5432:5432 \
        --publish 8081:8081 \
        --publish 8082:8082 \
        --publish 8083:8083 \
        --publish 9093:9093 \
        --publish 9094:9094 \
        --publish 9095:9095 \
        --publish 9096:9096 \
        --publish 9999:9999 \
        --publish 8082:8082 \
        --publish 9990:9990 \
        --publish 9991:9991 \
        --publish 9977:9977 \
        --publish 10000:10000 \
        --publish 10001:10001 \
        --publish 10010:10010
    # 5432: postgres
    # 8081: vertx-fib-demo-1 HTTP
    # 8082: vertx-fib-demo-2 HTTP
    # 8083: vertx-fib-demo-3 HTTP
    # 9093: vertx-fib-demo-1 RJMX
    # 9094: vertx-fib-demo-2 RJMX
    # 9095: vertx-fib-demo-3 RJMX
    # 9096: quarkus-test RJMX
    # 9097: quarkus-test-plugin RJMX
    # 9197: quarkus-test-plugin-2 RJMX
    # 9999: quarkus-test HTTP
    # 8082: Wildfly HTTP
    # 9990: Wildfly Admin Console
    # 9991: Wildfly RJMX
    # 9977: cryostat-reports agent callback
    # 10000: cryostat-reports RJMX
    # 10001: cryostat-reports HTTP
    # 10010: quarkus-test-plugin HTTP
    # 10011: quarkus-test-plugin-2 HTTP
}

function destroyPod() {
    podman pod stop cryostat-pod
    podman pod rm cryostat-pod
}
trap destroyPod EXIT

createPod
if [ "$1" = "postgres" ]; then
    runPostgres
elif [ "$1" = "postgres-pgcli" ]; then
    runPostgres
    PGPASSWORD=abcd1234 pgcli -h localhost -p 5432 -U postgres
    exit
fi
runDemoApps
runJfrDatasource
runGrafana
runReportGenerator
runCryostat "$1"
