#!/bin/sh
# shellcheck disable=SC3043

set -x
set -e

if [ -z "${PULL_IMAGES}" ]; then
    PULL_IMAGES="always"
fi

getPomProperty() {
    if command -v xpath > /dev/null 2>&1 ; then
        xpath -q -e "project/properties/$1/text()" pom.xml
    elif command -v mvnd > /dev/null 2>&1 ; then
        mvnd help:evaluate -o -B -q -DforceStdout -Dexpression="$1"
    else
        mvn help:evaluate -o -B -q -DforceStdout -Dexpression="$1"
    fi
}

runCryostat() {
    local DIR; local host; local datasourcePort; local grafanaPort;
    DIR="$(dirname "$(readlink -f "$0")")"
    host="$(getPomProperty cryostat.itest.webHost)"
    datasourcePort="$(getPomProperty cryostat.itest.jfr-datasource.port)"
    grafanaPort="$(getPomProperty cryostat.itest.grafana.port)"
    # credentials `user:pass`
    echo "user:d74ff0ee8da3b9806b18c877dbf29bbde50b5bd8e4dad7a3a725000feb82e8f1" > "./conf/cryostat-users.properties"

    if [ "$1" = "postgres" ]; then
        JDBC_URL="jdbc:postgresql://cryostat:5432/cryostat"
        JDBC_DRIVER="org.postgresql.Driver"
        HIBERNATE_DIALECT="org.hibernate.dialect.PostgreSQL95Dialect"
        JDBC_USERNAME="postgres"
        JDBC_PASSWORD="abcd1234"
        HBM2DDL="update"
    elif [ "$1" = "h2mem" ]; then
        JDBC_URL="jdbc:h2:mem:cryostat;DB_CLOSE_DELAY=-1;INIT=create domain if not exists jsonb as varchar"
        JDBC_DRIVER="org.h2.Driver"
        JDBC_USERNAME="cryostat"
        JDBC_PASSWORD=""
        HIBERNATE_DIALECT="org.hibernate.dialect.H2Dialect"
        HBM2DDL="create"
    else
        JDBC_URL="jdbc:h2:file:/opt/cryostat.d/conf.d/h2;INIT=create domain if not exists jsonb as varchar"
        JDBC_DRIVER="org.h2.Driver"
        JDBC_USERNAME="cryostat"
        JDBC_PASSWORD=""
        HIBERNATE_DIALECT="org.hibernate.dialect.H2Dialect"
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
        CRYOSTAT_JMX_CREDENTIALS_DB_PASSWORD="smoketest" \
        CRYOSTAT_HBM2DDL="$HBM2DDL" \
        CRYOSTAT_DEV_MODE="true" \
        exec "$DIR/run.sh"
}

runPostgres() {
    if [ ! -d "$(dirname "$0")/conf/postgres" ]; then
        mkdir "$(dirname "$0")/conf/postgres"
    fi
    if [ -z "${POSTGRES_IMAGE}" ]; then
        local image; local version;
        image="$(getPomProperty postgres.image)"
        version="$(getPomProperty postgres.version)"
        POSTGRES_IMAGE="${image}:${version}"
    fi
    podman run \
        --name postgres \
        --pod cryostat-pod \
        --env POSTGRES_USER=postgres \
        --env POSTGRES_PASSWORD=abcd1234 \
        --env POSTGRES_DB=cryostat \
        --env PGPASSWORD=abcd1234 \
        --mount type=bind,source="$(dirname "$0")/conf/postgres",destination=/var/lib/postgresql/data/pgdata,relabel=shared \
        --mount type=bind,source="$(dirname "$0")/src/test/resources/postgres",destination=/docker-entrypoint-initdb.d,relabel=shared \
        --env PGDATA=/var/lib/postgresql/data/pgdata \
        --rm -d "${POSTGRES_IMAGE}"
}

runDemoApps() {
    local webPort;
    if [ -z "$CRYOSTAT_WEB_PORT" ]; then
        webPort="$(getPomProperty cryostat.itest.webPort)"
    else
        webPort="${CRYOSTAT_WEB_PORT}"
    fi
    if [ -z "$CRYOSTAT_DISABLE_SSL" ]; then
        local protocol="https"
    else
        local protocol="http"
    fi

    podman run \
        --name jmxquarkus \
        --pod cryostat-pod \
        --label io.cryostat.discovery="true" \
        --label io.cryostat.jmxPort="51423" \
        --env QUARKUS_HTTP_PORT=10012 \
        --rm -d quay.io/roberttoyonaga/jmx:jmxquarkus@sha256:b067f29faa91312d20d43c55d194a2e076de7d0d094da3d43ee7d2b2b5a6f100

    podman run \
        --name vertx-fib-demo-0 \
        --env HTTP_PORT=8079 \
        --env JMX_PORT=9089 \
        --env START_DELAY=60 \
        --pod cryostat-pod \
        --label io.cryostat.discovery="true" \
        --label io.cryostat.jmxHost="vertx-fib-demo-0" \
        --label io.cryostat.jmxPort="9089" \
        --rm -d quay.io/andrewazores/vertx-fib-demo:0.13.1

    podman run \
        --name vertx-fib-demo-1 \
        --env HTTP_PORT=8081 \
        --env JMX_PORT=9093 \
        --env CRYOSTAT_AGENT_APP_NAME="vertx-fib-demo-1" \
        --env CRYOSTAT_AGENT_WEBCLIENT_SSL_TRUST_ALL="true" \
        --env CRYOSTAT_AGENT_WEBCLIENT_SSL_VERIFY_HOSTNAME="false" \
        --env CRYOSTAT_AGENT_WEBSERVER_HOST="localhost" \
        --env CRYOSTAT_AGENT_WEBSERVER_PORT="8910" \
        --env CRYOSTAT_AGENT_CALLBACK="http://localhost:8910/" \
        --env CRYOSTAT_AGENT_BASEURI="${protocol}://localhost:${webPort}/" \
        --env CRYOSTAT_AGENT_TRUST_ALL="true" \
        --env CRYOSTAT_AGENT_AUTHORIZATION="Basic $(echo user:pass | base64)" \
        --pod cryostat-pod \
        --label io.cryostat.discovery="true" \
        --label io.cryostat.jmxHost="vertx-fib-demo-1" \
        --label io.cryostat.jmxPort="9093" \
        --rm -d quay.io/andrewazores/vertx-fib-demo:0.13.1

    podman run \
        --name vertx-fib-demo-2 \
        --env HTTP_PORT=8082 \
        --env JMX_PORT=9094 \
        --env USE_AUTH=true \
        --env CRYOSTAT_AGENT_APP_NAME="vertx-fib-demo-2" \
        --env CRYOSTAT_AGENT_WEBCLIENT_SSL_TRUST_ALL="true" \
        --env CRYOSTAT_AGENT_WEBCLIENT_SSL_VERIFY_HOSTNAME="false" \
        --env CRYOSTAT_AGENT_WEBSERVER_HOST="localhost" \
        --env CRYOSTAT_AGENT_WEBSERVER_PORT="8911" \
        --env CRYOSTAT_AGENT_CALLBACK="http://localhost:8911/" \
        --env CRYOSTAT_AGENT_BASEURI="${protocol}://localhost:${webPort}/" \
        --env CRYOSTAT_AGENT_TRUST_ALL="true" \
        --env CRYOSTAT_AGENT_AUTHORIZATION="Basic $(echo user:pass | base64)" \
        --pod cryostat-pod \
        --label io.cryostat.discovery="true" \
        --label io.cryostat.jmxHost="vertx-fib-demo-2" \
        --label io.cryostat.jmxPort="9094" \
        --label io.cryostat.jmxUrl="service:jmx:rmi:///jndi/rmi://vertx-fib-demo-2:9094/jmxrmi" \
        --rm -d quay.io/andrewazores/vertx-fib-demo:0.13.1

    podman run \
        --name vertx-fib-demo-3 \
        --env HTTP_PORT=8083 \
        --env JMX_PORT=9095 \
        --env USE_SSL=true \
        --env USE_AUTH=true \
        --env CRYOSTAT_AGENT_APP_NAME="vertx-fib-demo-3" \
        --env CRYOSTAT_AGENT_WEBCLIENT_SSL_TRUST_ALL="true" \
        --env CRYOSTAT_AGENT_WEBCLIENT_SSL_VERIFY_HOSTNAME="false" \
        --env CRYOSTAT_AGENT_WEBSERVER_HOST="localhost" \
        --env CRYOSTAT_AGENT_WEBSERVER_PORT="8912" \
        --env CRYOSTAT_AGENT_CALLBACK="http://localhost:8912/" \
        --env CRYOSTAT_AGENT_BASEURI="${protocol}://localhost:${webPort}/" \
        --env CRYOSTAT_AGENT_TRUST_ALL="true" \
        --env CRYOSTAT_AGENT_AUTHORIZATION="Basic $(echo user:pass | base64)" \
        --pod cryostat-pod \
        --label io.cryostat.discovery="true" \
        --label io.cryostat.jmxUrl="service:jmx:rmi:///jndi/rmi://vertx-fib-demo-3:9095/jmxrmi" \
        --rm -d quay.io/andrewazores/vertx-fib-demo:0.13.1

    # this config is broken on purpose (missing required env vars) to test the agent's behaviour
    # when not properly set up
    podman run \
        --name quarkus-test-agent-0 \
        --pod cryostat-pod \
        --env JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager -javaagent:/deployments/app/cryostat-agent.jar" \
        --env QUARKUS_HTTP_PORT=10009 \
        --env ORG_ACME_CRYOSTATSERVICE_ENABLED="false" \
        --env CRYOSTAT_AGENT_WEBCLIENT_SSL_TRUST_ALL="true" \
        --env CRYOSTAT_AGENT_WEBCLIENT_SSL_VERIFY_HOSTNAME="false" \
        --rm -d quay.io/andrewazores/quarkus-test:latest

    podman run \
        --name quarkus-test-agent-1 \
        --pod cryostat-pod \
        --env JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager -Dcom.sun.management.jmxremote.port=9097 -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.authenticate=false -javaagent:/deployments/app/cryostat-agent.jar" \
        --env QUARKUS_HTTP_PORT=10010 \
        --env ORG_ACME_CRYOSTATSERVICE_ENABLED="false" \
        --env CRYOSTAT_AGENT_APP_NAME="quarkus-test-agent-1" \
        --env CRYOSTAT_AGENT_WEBCLIENT_SSL_TRUST_ALL="true" \
        --env CRYOSTAT_AGENT_WEBCLIENT_SSL_VERIFY_HOSTNAME="false" \
        --env CRYOSTAT_AGENT_WEBSERVER_HOST="localhost" \
        --env CRYOSTAT_AGENT_WEBSERVER_PORT="9977" \
        --env CRYOSTAT_AGENT_CALLBACK="http://localhost:9977/" \
        --env CRYOSTAT_AGENT_BASEURI="${protocol}://localhost:${webPort}/" \
        --env CRYOSTAT_AGENT_TRUST_ALL="true" \
        --env CRYOSTAT_AGENT_AUTHORIZATION="Basic $(echo user:pass | base64)" \
        --env CRYOSTAT_AGENT_HARVESTER_PERIOD_MS=60000 \
        --env CRYOSTAT_AGENT_HARVESTER_MAX_FILES=10 \
        --rm -d quay.io/andrewazores/quarkus-test:latest

    podman run \
        --name quarkus-test-agent-2 \
        --pod cryostat-pod \
        --env JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager -javaagent:/deployments/app/cryostat-agent.jar" \
        --env QUARKUS_HTTP_PORT=10011 \
        --env ORG_ACME_CRYOSTATSERVICE_ENABLED="false" \
        --env CRYOSTAT_AGENT_APP_NAME="quarkus-test-agent-2" \
        --env CRYOSTAT_AGENT_WEBCLIENT_SSL_TRUST_ALL="true" \
        --env CRYOSTAT_AGENT_WEBCLIENT_SSL_VERIFY_HOSTNAME="false" \
        --env CRYOSTAT_AGENT_WEBSERVER_HOST="localhost" \
        --env CRYOSTAT_AGENT_WEBSERVER_PORT="9988" \
        --env CRYOSTAT_AGENT_CALLBACK="http://localhost:9988/" \
        --env CRYOSTAT_AGENT_BASEURI="${protocol}://localhost:${webPort}/" \
        --env CRYOSTAT_AGENT_TRUST_ALL="true" \
        --env CRYOSTAT_AGENT_AUTHORIZATION="Basic $(echo user:pass | base64)" \
        --env CRYOSTAT_AGENT_API_WRITES_ENABLED="true" \
        --rm -d quay.io/andrewazores/quarkus-test:latest

    # copy a jboss-client.jar into /clientlib first
    # manual entry URL: service:jmx:remote+http://localhost:9990
    podman run \
        --name wildfly \
        --pod cryostat-pod \
        --rm -d quay.io/andrewazores/wildfly-demo:v0.0.1
}

runJfrDatasource() {
    if [ -z "${DATASOURCE_IMAGE}" ]; then
        local stream; local tag;
        stream="$(getPomProperty cryostat.itest.jfr-datasource.imageStream)"
        tag="$(getPomProperty cryostat.itest.jfr-datasource.version)"
        DATASOURCE_IMAGE="${stream}:${tag}"
    fi
    # limits set to match operator defaults:
    # https://github.com/cryostatio/cryostat-operator/blob/2d386930dc96f0dcaf937987ec35874006c53b61/internal/controllers/common/resource_definitions/resource_definitions.go#L66
    local RJMX_PORT=10111
    podman run \
        --name jfr-datasource \
        --pull "${PULL_IMAGES}" \
        --pod cryostat-pod \
        --cpus 0.2 \
        --memory 384M \
        --label io.cryostat.discovery="true" \
        --label io.cryostat.jmxHost="localhost" \
        --label io.cryostat.jmxPort="${RJMX_PORT}" \
        --restart on-failure \
        --env JAVA_OPTS_APPEND="-XX:-ExitOnOutOfMemoryError -Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager -Dcom.sun.management.jmxremote.autodiscovery=true -Dcom.sun.management.jmxremote.port=${RJMX_PORT} -Dcom.sun.management.jmxremote.rmi.port=${RJMX_PORT} -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false" \
        --rm -d "${DATASOURCE_IMAGE}"
}

runGrafana() {
    if [ -z "${GRAFANA_IMAGE}" ]; then
        local stream; local tag;
        stream="$(getPomProperty cryostat.itest.grafana.imageStream)"
        tag="$(getPomProperty cryostat.itest.grafana.version)"
        GRAFANA_IMAGE="${stream}:${tag}"
    fi
    # limits set to match operator defaults:
    # https://github.com/cryostatio/cryostat-operator/blob/2d386930dc96f0dcaf937987ec35874006c53b61/internal/controllers/common/resource_definitions/resource_definitions.go#L66
    local host; local port;
    host="$(getPomProperty cryostat.itest.webHost)"
    port="$(getPomProperty cryostat.itest.jfr-datasource.port)"
    podman run \
        --name grafana \
        --pull "${PULL_IMAGES}" \
        --pod cryostat-pod \
        --cpus 0.1 \
        --memory 120M \
        --env GF_INSTALL_PLUGINS=grafana-simple-json-datasource \
        --env GF_AUTH_ANONYMOUS_ENABLED=true \
        --env JFR_DATASOURCE_URL="http://${host}:${port}" \
        --rm -d "${GRAFANA_IMAGE}"
}

runReportGenerator() {
    if [ -z "${REPORTS_IMAGE}" ]; then
        local stream; local tag;
        stream="$(getPomProperty cryostat.itest.reports.imageStream)"
        tag="$(getPomProperty cryostat.itest.reports.version)"
        REPORTS_IMAGE="${stream}:${tag}"
    fi
    # limits set to match operator defaults:
    # https://github.com/cryostatio/cryostat-operator/blob/2d386930dc96f0dcaf937987ec35874006c53b61/internal/controllers/common/resource_definitions/resource_definitions.go#L66
    local RJMX_PORT=10000
    local port;
    port="$(getPomProperty cryostat.itest.reports.port)"
    podman run \
        --name reports \
        --pull "${PULL_IMAGES}" \
        --pod cryostat-pod \
        --label io.cryostat.discovery="true" \
        --label io.cryostat.jmxHost="localhost" \
        --label io.cryostat.jmxPort="${RJMX_PORT}" \
        --cpus 0.2 \
        --memory 384M \
        --restart on-failure \
        --env JAVA_OPTS_APPEND="-XX:-ExitOnOutOfMemoryError -Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager -Dcom.sun.management.jmxremote.autodiscovery=true -Dcom.sun.management.jmxremote.port=${RJMX_PORT} -Dcom.sun.management.jmxremote.rmi.port=${RJMX_PORT} -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false" \
        --env QUARKUS_HTTP_PORT="${port}" \
        --rm -d "${REPORTS_IMAGE}"
}

createPod() {
    local webPort; local datasourcePort; local grafanaPort;
    webPort="$(getPomProperty cryostat.webPort)"
    datasourcePort="$(getPomProperty cryostat.itest.jfr-datasource.port)"
    grafanaPort="$(getPomProperty cryostat.itest.grafana.port)"
    podman pod create \
        --replace \
        --hostname cryostat \
        --name cryostat-pod \
        --publish "${webPort}:${webPort}" \
        --publish "${datasourcePort}:${datasourcePort}" \
        --publish "${grafanaPort}:${grafanaPort}" \
        --publish 5432:5432 \
        --publish 8081:8081 \
        --publish 8082:8082 \
        --publish 8083:8083 \
        --publish 8082:8082 \
        --publish 9990:9990 \
        --publish 10001:10001 \
        --publish 10010:10010
    # 5432: postgres
    # 8081: vertx-fib-demo-1 HTTP
    # 8082: vertx-fib-demo-2 HTTP
    # 8083: vertx-fib-demo-3 HTTP
    # 9990: Wildfly Admin Console
    # 10001: cryostat-reports HTTP
    # 10010: quarkus-test-agent-1 HTTP
    # 10011: quarkus-test-agent-2 HTTP
}

destroyPod() {
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
