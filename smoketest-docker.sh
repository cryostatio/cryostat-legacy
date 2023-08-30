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
    local DIR; local datasourcePort; local grafanaPort;
    DIR="$(dirname "$(readlink -f "$0")")"
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

    GRAFANA_DATASOURCE_URL="http://jfr-datasource:${datasourcePort}" \
        GRAFANA_DASHBOARD_URL="http://grafana:${grafanaPort}" \
        CRYOSTAT_RJMX_USER=smoketest \
        CRYOSTAT_RJMX_PASS=smoketest \
        CRYOSTAT_ALLOW_UNTRUSTED_SSL=true \
        CRYOSTAT_REPORT_GENERATOR="http://reports:10001" \
        CRYOSTAT_AUTH_MANAGER="$CRYOSTAT_AUTH_MANAGER" \
        CRYOSTAT_JDBC_URL="$JDBC_URL" \
        CRYOSTAT_JDBC_DRIVER="$JDBC_DRIVER" \
        CRYOSTAT_HIBERNATE_DIALECT="$HIBERNATE_DIALECT" \
        CRYOSTAT_JDBC_USERNAME="$JDBC_USERNAME" \
        CRYOSTAT_JDBC_PASSWORD="$JDBC_PASSWORD" \
        CRYOSTAT_JMX_CREDENTIALS_DB_PASSWORD="smoketest" \
        CRYOSTAT_HBM2DDL="$HBM2DDL" \
        CRYOSTAT_DEV_MODE="true" \
        CONTAINERS="${CONTAINERS}" \
        exec "$DIR/run-docker.sh"
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
    docker run \
        --name postgres \
        --network cryostat-docker \
        --env POSTGRES_USER=postgres \
        --env POSTGRES_PASSWORD=abcd1234 \
        --env POSTGRES_DB=cryostat \
        --env PGPASSWORD=abcd1234 \
        --mount type=bind,source="$(dirname "$0")/conf/postgres",destination=/var/lib/postgresql/data/pgdata,relabel=shared \
        --mount type=bind,source="$(dirname "$0")/src/test/resources/postgres",destination=/docker-entrypoint-initdb.d,relabel=shared \
        --env PGDATA=/var/lib/postgresql/data/pgdata \
        --rm -d "${POSTGRES_IMAGE}"
    CONTAINERS="${CONTAINERS:+${CONTAINERS} }postgres"
}

runDemoApps() {
    docker run \
        --name vertx-fib-demo-1 \
        --network cryostat-docker \
        --env HTTP_PORT=8081 \
        --env JMX_PORT=9093 \
        --label io.cryostat.discovery="true" \
        --label io.cryostat.jmxPort="9093" \
        --publish 8081:8081 \
        --publish 9093:9093 \
        --rm -d quay.io/andrewazores/vertx-fib-demo:0.13.0
    CONTAINERS="${CONTAINERS:+${CONTAINERS} }vertx-fib-demo-1"

    docker run \
        --name vertx-fib-demo-2 \
        --network cryostat-docker \
        --env HTTP_PORT=8082 \
        --env JMX_PORT=9094 \
        --env USE_AUTH=true \
        --label io.cryostat.discovery="true" \
        --label io.cryostat.jmxPort="9094" \
        --publish 8082:8082 \
        --publish 9094:9092 \
        --rm -d quay.io/andrewazores/vertx-fib-demo:0.13.0
    CONTAINERS="${CONTAINERS:+${CONTAINERS} }vertx-fib-demo-2"

    docker run \
        --name vertx-fib-demo-3 \
        --network cryostat-docker \
        --env HTTP_PORT=8083 \
        --env JMX_PORT=9095 \
        --env USE_SSL=true \
        --env USE_AUTH=true \
        --label io.cryostat.discovery="true" \
        --label io.cryostat.jmxPort="9095" \
        --publish 8083:8083 \
        --publish 9095:9095 \
        --rm -d quay.io/andrewazores/vertx-fib-demo:0.13.0
    CONTAINERS="${CONTAINERS:+${CONTAINERS} }vertx-fib-demo-3"

    # this config is broken on purpose (missing required env vars) to test the agent's behaviour
    # when not properly set up
    docker run \
        --name quarkus-test-agent-0 \
        --network cryostat-docker \
        --env JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager -javaagent:/deployments/app/cryostat-agent.jar" \
        --env QUARKUS_HTTP_PORT=10009 \
        --env ORG_ACME_CRYOSTATSERVICE_ENABLED="false" \
        --publish 10009:10009 \
        --rm -d quay.io/andrewazores/quarkus-test:latest
    CONTAINERS="${CONTAINERS:+${CONTAINERS} }quarkus-test-agent-0"

    docker run \
        --name quarkus-test-agent-1 \
        --network cryostat-docker \
        --env JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager -Dcom.sun.management.jmxremote.port=9097 -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.authenticate=false -javaagent:/deployments/app/cryostat-agent.jar" \
        --env QUARKUS_HTTP_PORT=10010 \
        --env ORG_ACME_CRYOSTATSERVICE_ENABLED="false" \
        --publish 10010:10010 \
        --rm -d quay.io/andrewazores/quarkus-test:latest
    CONTAINERS="${CONTAINERS:+${CONTAINERS} }quarkus-test-agent-1"

    docker run \
        --name quarkus-test-agent-2 \
        --network cryostat-docker \
        --env JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager -javaagent:/deployments/app/cryostat-agent.jar" \
        --env QUARKUS_HTTP_PORT=10011 \
        --env ORG_ACME_CRYOSTATSERVICE_ENABLED="false" \
        --publish 10011:10011 \
        --rm -d quay.io/andrewazores/quarkus-test:latest
    CONTAINERS="${CONTAINERS:+${CONTAINERS} }quarkus-test-agent-2"

    # copy a jboss-client.jar into /clientlib first
    # manual entry URL: service:jmx:remote+http://localhost:9990
    docker run \
        --name wildfly \
        --network cryostat-docker \
        --publish 9990:9990 \
        --publish 9991:9991 \
        --rm -d quay.io/andrewazores/wildfly-demo:v0.0.1
    CONTAINERS="${CONTAINERS:+${CONTAINERS} }wildfly"
}

runJfrDatasource() {
    if [ -z "${DATASOURCE_IMAGE}" ]; then
        local stream; local tag;
        stream="$(getPomProperty cryostat.itest.jfr-datasource.imageStream)"
        tag="$(getPomProperty cryostat.itest.jfr-datasource.version)"
        DATASOURCE_IMAGE="${stream}:${tag}"
    fi
    docker run \
        --name jfr-datasource \
        --network cryostat-docker \
        --pull "${PULL_IMAGES}" \
        --rm -d "${DATASOURCE_IMAGE}"
    CONTAINERS="${CONTAINERS:+${CONTAINERS} }jfr-datasource"
}

runGrafana() {
    if [ -z "${GRAFANA_IMAGE}" ]; then
        local stream; local tag;
        stream="$(getPomProperty cryostat.itest.grafana.imageStream)"
        tag="$(getPomProperty cryostat.itest.grafana.version)"
        GRAFANA_IMAGE="${stream}:${tag}"
    fi
    local host; local port;
    host="$(getPomProperty cryostat.itest.webHost)"
    port="$(getPomProperty cryostat.itest.jfr-datasource.port)"
    docker run \
        --name grafana \
        --network cryostat-docker \
        --pull "${PULL_IMAGES}" \
        --publish 3000:3000 \
        --publish "${port}:${port}" \
        --env GF_INSTALL_PLUGINS=grafana-simple-json-datasource \
        --env GF_AUTH_ANONYMOUS_ENABLED=true \
        --env JFR_DATASOURCE_URL="http://${host}:${port}" \
        --rm -d "${GRAFANA_IMAGE}"
    CONTAINERS="${CONTAINERS:+${CONTAINERS} }grafana"
}

runReportGenerator() {
    if [ -z "${REPORTS_IMAGE}" ]; then
        local stream; local tag;
        stream="$(getPomProperty cryostat.itest.reports.imageStream)"
        tag="$(getPomProperty cryostat.itest.reports.version)"
        REPORTS_IMAGE="${stream}:${tag}"
    fi
    local RJMX_PORT=10000
    local port;
    port="$(getPomProperty cryostat.itest.reports.port)"
    docker run \
        --name reports \
        --network cryostat-docker \
        --pull "${PULL_IMAGES}" \
        --label io.cryostat.discovery="true" \
        --label io.cryostat.jmxPort="${RJMX_PORT}" \
        --cpus 1 \
        --publish "${RJMX_PORT}:${RJMX_PORT}" \
        --publish 10001:10001 \
        --memory 512M \
        --env JAVA_OPTS="-XX:ActiveProcessorCount=1 -Dcom.sun.management.jmxremote.autodiscovery=true -Dcom.sun.management.jmxremote.port=${RJMX_PORT} -Dcom.sun.management.jmxremote.rmi.port=${RJMX_PORT} -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false" \
        --env QUARKUS_HTTP_PORT="${port}" \
        --rm -d "${REPORTS_IMAGE}"
    CONTAINERS="${CONTAINERS:+${CONTAINERS} }reports"
}


dockerCleanUp() {
    # shellcheck disable=SC2086
    if [ -n "${CONTAINERS}" ]; then
        docker rm -f ${CONTAINERS}
    fi
    docker network rm -f cryostat-docker
}
trap dockerCleanUp EXIT

if [ "$1" = "postgres" ]; then
    runPostgres
elif [ "$1" = "postgres-pgcli" ]; then
    runPostgres
    PGPASSWORD=abcd1234 pgcli -h localhost -p 5432 -U postgres
    exit
fi
docker network create --driver bridge cryostat-docker
runDemoApps
runJfrDatasource
runGrafana
runReportGenerator
runCryostat "$1"
