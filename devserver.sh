#!/bin/sh
# shellcheck disable=SC3043

work_dir="$(mktemp -d)"

reports_container="cryostat-devserver-reports"
datasource_container="cryostat-devserver-jfr-datasource"
grafana_container="cryostat-devserver-grafana-dashboard"
podname="cryostat-devserver"

getPomProperty() {
    if command -v xpath > /dev/null 2>&1 ; then
        xpath -q -e "project/properties/$1/text()" pom.xml
    elif command -v mvnd > /dev/null 2>&1 ; then
        mvnd help:evaluate -o -B -q -DforceStdout -Dexpression="$1"
    else
        mvn help:evaluate -o -B -q -DforceStdout -Dexpression="$1"
    fi
}

cleanup() {
    podman pod stop "${podname}"
    podman pod rm "${podname}"
    rm -rf "${work_dir}"
}
trap cleanup EXIT

if [ -z "${MVN}" ]; then
    MVN="$(which mvn)"
fi

for i in archive clientlib conf templates probes; do
    if [ -e "${work_dir}/${i}" ]; then
        if [ ! -d "${work_dir}/${i}" ]; then
            echo "${work_dir}/${i} already exists but is not a directory"
            exit 1
        fi
    else
        mkdir "${work_dir}/${i}"
    fi
done

if [ -z "$CRYOSTAT_AUTH_MANAGER" ]; then
    CRYOSTAT_AUTH_MANAGER="io.cryostat.net.BasicAuthManager"
fi
if  [ "$CRYOSTAT_AUTH_MANAGER" = "io.cryostat.net.BasicAuthManager" ]; then
    # credentials `user:pass`
    echo "user:d74ff0ee8da3b9806b18c877dbf29bbde50b5bd8e4dad7a3a725000feb82e8f1" > "${work_dir}/conf/cryostat-users.properties"
fi

if [ -z "$CRYOSTAT_CORS_ORIGIN" ]; then
    "${MVN}" -DskipTests=true -DskipBaseImage=true -Djib.skip=true clean prepare-package
else
    "${MVN}" -DskipTests=true -DskipBaseImage=true -Djib.skip=true -Dheadless=true clean prepare-package
fi

# HACK. The vertx-maven-plugin doesn't include target/assets on the classpath, so copy its contents into target/classes which is included
cp -r target/assets/app/resources/* target/classes

flags="-Dcom.sun.management.jmxremote.autodiscovery=true -Dcom.sun.management.jmxremote.port=9091 -Dcom.sun.management.jmxremote.rmi.port=9091 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.registry.ssl=false"

createPod() {
    podman pod create \
        --replace \
        --hostname cryostat \
        --name "${podname}" \
        --publish 3000:3000 \
        --publish 8080:8080 \
        --publish 10001:10001
}

runReportGenerator() {
    local stream; local tag; local port;
    stream="$(getPomProperty cryostat.itest.reports.imageStream)"
    tag="$(getPomProperty cryostat.itest.reports.version)"
    port="$(getPomProperty cryostat.itest.reports.port)"
    podman run \
        --name "${reports_container}" \
        --pod "${podname}" \
        --restart on-failure \
        --env QUARKUS_HTTP_PORT="${port}" \
        --rm -d "${stream}:${tag}"
}

runJfrDatasource() {
    local stream; local tag;
    stream="$(getPomProperty cryostat.itest.jfr-datasource.imageStream)"
    tag="$(getPomProperty cryostat.itest.jfr-datasource.version)"
    podman run \
        --name "${datasource_container}" \
        --pod "${podname}" \
        --rm -d "${stream}:${tag}"
}

runGrafana() {
    local stream; local tag;
    stream="$(getPomProperty cryostat.itest.grafana.imageStream)"
    tag="$(getPomProperty cryostat.itest.grafana.version)"
    podman run \
        --name "${grafana_container}" \
        --pod "${podname}" \
        --env GF_INSTALL_PLUGINS=grafana-simple-json-datasource \
        --env GF_AUTH_ANONYMOUS_ENABLED=true \
        --env JFR_DATASOURCE_URL="http://localhost:8080" \
        --rm -d "${stream}:${tag}"
}

createPod
runReportGenerator
runJfrDatasource
runGrafana

MAVEN_OPTS="${flags}" \
    CRYOSTAT_PLATFORM=io.cryostat.platform.internal.DefaultPlatformStrategy \
    CRYOSTAT_DISABLE_SSL=true \
    CRYOSTAT_DISABLE_JMX_AUTH=true \
    CRYOSTAT_WEB_HOST=localhost \
    CRYOSTAT_WEB_PORT=8181 \
    CRYOSTAT_CORS_ORIGIN="${CRYOSTAT_CORS_ORIGIN}" \
    CRYOSTAT_REPORT_GENERATOR="http://localhost:10001" \
    GRAFANA_DATASOURCE_URL="http://localhost:8080" \
    GRAFANA_DASHBOARD_URL="http://localhost:3000" \
    CRYOSTAT_AUTH_MANAGER="${CRYOSTAT_AUTH_MANAGER}" \
    CRYOSTAT_ARCHIVE_PATH="${work_dir}/archive" \
    CRYOSTAT_CLIENTLIB_PATH="${work_dir}/clientlib" \
    CRYOSTAT_CONFIG_PATH="${work_dir}/conf" \
    CRYOSTAT_TEMPLATE_PATH="${work_dir}/templates" \
    CRYOSTAT_PROBE_TEMPLATE_PATH="${work_dir}/probes" \
    CRYOSTAT_JMX_CREDENTIALS_DB_PASSWORD=devserver \
    CRYOSTAT_DEV_MODE=true \
    "${MVN}" -Dheadless=true -DskipBaseImage=true -Djib.skip=true -DskipTests=true vertx:run
