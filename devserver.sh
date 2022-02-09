#!/bin/sh

work_dir="$(mktemp -d)"

podname="cryostat-devserver"

function cleanup() {
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

if [ -z "$CRYOSTAT_CORS_ORIGIN" ]; then
    "${MVN}" -DskipTests=true clean prepare-package
else
    "${MVN}" -DskipTests=true -Dcryostat.minimal=true clean prepare-package
fi

# HACK. The vertx-maven-plugin doesn't include target/assets on the classpath, so copy its contents into target/classes which is included
cp -r target/assets/app/resources/* target/classes

flags=(
    "-Dcom.sun.management.jmxremote.autodiscovery=true"
    "-Dcom.sun.management.jmxremote.port=9091"
    "-Dcom.sun.management.jmxremote.rmi.port=9091"
    "-Dcom.sun.management.jmxremote.authenticate=false"
    "-Dcom.sun.management.jmxremote.ssl=false"
    "-Dcom.sun.management.jmxremote.registry.ssl=false"
)


function createPod() {
    podman pod create \
        --replace \
        --hostname cryostat \
        --name "${podname}"
        --publish 3000:3000 \
        --publish 8080:8080 \
        --publish 10001:10001
}

function runReportGenerator() {
    podman run \
        --name "${reports_container}" \
        --pod "${podname}"
        --restart on-failure \
        --env QUARKUS_HTTP_PORT=10001 \
        --rm -d quay.io/cryostat/cryostat-reports:latest
}

function runJfrDatasource() {
    local stream="$(xpath -q -e 'project/properties/cryostat.itest.jfr-datasource.imageStream/text()' pom.xml)"
    local tag="$(xpath -q -e 'project/properties/cryostat.itest.jfr-datasource.version/text()' pom.xml)"
    podman run \
        --name "${datasource_container}" \
        --pod "${podname}"
        --rm -d "${stream}:${tag}"
}

function runGrafana() {
    local stream="$(xpath -q -e 'project/properties/cryostat.itest.grafana.imageStream/text()' pom.xml)"
    local tag="$(xpath -q -e 'project/properties/cryostat.itest.grafana.version/text()' pom.xml)"
    podman run \
        --name "${grafana_container}" \
        --pod "${podname}"
        --env GF_INSTALL_PLUGINS=grafana-simple-json-datasource \
        --env GF_AUTH_ANONYMOUS_ENABLED=true \
        --env JFR_DATASOURCE_URL="http://localhost:8080" \
        --rm -d "${stream}:${tag}"
}

createPod
runReportGenerator
runJfrDatasource
runGrafana

MAVEN_OPTS="${flags[@]}" \
    CRYOSTAT_PLATFORM=io.cryostat.platform.internal.DefaultPlatformStrategy \
    CRYOSTAT_DISABLE_SSL=true \
    CRYOSTAT_DISABLE_JMX_AUTH=true \
    CRYOSTAT_WEB_HOST=localhost \
    CRYOSTAT_WEB_PORT=8181 \
    CRYOSTAT_CORS_ORIGIN="${CRYOSTAT_CORS_ORIGIN}" \
    CRYOSTAT_REPORT_GENERATOR="http://localhost:10001" \
    GRAFANA_DATASOURCE_URL="http://localhost:8080" \
    GRAFANA_DASHBOARD_URL="http://localhost:3000" \
    CRYOSTAT_AUTH_MANAGER=io.cryostat.net.NoopAuthManager \
    CRYOSTAT_ARCHIVE_PATH="${work_dir}/archive" \
    CRYOSTAT_CLIENTLIB_PATH="${work_dir}/clientlib" \
    CRYOSTAT_CONFIG_PATH="${work_dir}/conf" \
    CRYOSTAT_TEMPLATE_PATH="${work_dir}/templates" \
    CRYOSTAT_PROBE_TEMPLATE_PATH="${work_dir}/probes" \
    "${MVN}" -Dcryostat.minimal=true -DskipTests=true vertx:run
