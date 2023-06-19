#!/bin/bash
# shellcheck disable=SC1091,SC2086

set -ex

source "$(dirname "$0")/.env"
# podman-compose .env files are ignored https://github.com/containers/podman-compose/issues/475
export COMPOSE_PROJECT_NAME
export COMPOSE_FILE

# handle compose engine
if [[ -z "${CONTAINER_ENGINE}" ]]; then
    CONTAINER_ENGINE="podman"
fi

if [[ "${CONTAINER_ENGINE}" == "podman" ]]; then
    COMPOSE_ENGINE="podman-compose"
elif [[ "${CONTAINER_ENGINE}" == "docker" ]]; then
    COMPOSE_ENGINE="docker compose"
else
    echo "ERROR: Invalid container engine specified."
    exit 1
fi

if [ -z "$COMPOSE_PROFILES" ]; then
    COMPOSE_PROFILES=""
fi

compose_files=(
    "compose/compose-cryostat.yaml"
    "compose/compose-cryostat-reports.yaml"
    "compose/compose-cryostat-grafana.yaml"
    "compose/compose-jfr-datasource.yaml"
    "compose/compose-jmxquarkus.yaml"
)

display_usage() {
    echo "Usage: $(basename "$0") [-a targets] [-j targets] [-d] [-i] [-p] [-r] [-s] [-A]"
    echo "Options:"
    echo "  -a targets  Sets # of agent targets"
    echo "  -j targets  Sets # of JMX targets"
    echo "  -d          Enables Cryostat duplicate"
    echo "  -i          Enables invalid targets"
    echo "  -p          Enables a postgres database"
    echo "  -r          Enables automated rule that matches all targets"
    echo "  -s          Enables periodic target scaling/restarting"
    echo "  -A          Enables all of the above; Sets targets to 10 each"
}


# Parse command-line options
while getopts ":a:j:diprsA" opt; do
    case $opt in
        a)
            CT_AGENT_REPLICAS=$OPTARG
            ;;
        j)
            CT_JMX_REPLICAS=$OPTARG
            ;;
        d)
            CT_EN_DUPLICATE=true
            ;;
        i)
            CT_EN_INVALID=true
            ;;
        p)
            CT_EN_POSTGRES=true
            ;;
        r)
            CT_EN_RULES=true
            ;;
        s)
            CT_EN_SCALING=true
            ;;
        A)
            # shellcheck disable=SC2034
            CT_AGENT_REPLICAS=10
            # shellcheck disable=SC2034
            CT_JMX_REPLICAS=10
            CT_EN_DUPLICATE=true
            CT_EN_INVALID=true
            CT_EN_POSTGRES=true
            CT_EN_RULES=true
            CT_EN_SCALING=true
            ;;
        \?)
            echo "Invalid option: -$OPTARG"
            display_usage
            exit 1
            ;;
    esac
done

shift $((OPTIND - 1))

getPomProperty() {
    if command -v xpath > /dev/null 2>&1 ; then
        xpath -q -e "project/properties/$1/text()" pom.xml
    elif command -v mvnd > /dev/null 2>&1 ; then
        mvnd help:evaluate -o -B -q -DforceStdout -Dexpression="$1"
    else
        mvn help:evaluate -o -B -q -DforceStdout -Dexpression="$1"
    fi
}

compose_down() {
    $COMPOSE_ENGINE down --remove-orphans
    if [ -n "$child_pid" ]; then
        kill "$child_pid"
    fi
}

cleanup() {
    rm -f "$tmp_yaml"
}

if [ -z "$CRYOSTAT_DISABLE_SSL" ]; then
    protocol="https"
else
    protocol="http"
fi

# grafana
grafanaStream="$(getPomProperty cryostat.itest.grafana.imageStream)"
grafanaTag="$(getPomProperty cryostat.itest.grafana.version)"
GRAFANA_IMAGE="${grafanaStream}:${grafanaTag}"
datasourceStream="$(getPomProperty cryostat.itest.jfr-datasource.imageStream)"
datasourceTag="$(getPomProperty cryostat.itest.jfr-datasource.version)"
DATASOURCE_IMAGE="${datasourceStream}:${datasourceTag}"
export GRAFANA_IMAGE
export DATASOURCE_IMAGE

# reports
reportsStream="$(getPomProperty cryostat.itest.reports.imageStream)"
reportsTag="$(getPomProperty cryostat.itest.reports.version)"
REPORTS_RJMX_PORT=10000
REPORTS_HTTP_PORT="$(getPomProperty cryostat.itest.reports.port)"
REPORTS_IMAGE="${reportsStream}:${reportsTag}"
export REPORTS_IMAGE
export REPORTS_RJMX_PORT
export REPORTS_HTTP_PORT

# agent app configuration
CRYOSTAT_AGENT_AUTHORIZATION="Basic $(echo user:pass | base64)"
CRYOSTAT_AGENT_BASEURI="${protocol}://cryostat:8181/"
export CRYOSTAT_AGENT_AUTHORIZATION
export CRYOSTAT_AGENT_BASEURI

# postgres
if [ "$CT_EN_POSTGRES" = true ]; then
    if [ -z "${POSTGRES_IMAGE}" ]; then
        postgresStream="$(getPomProperty postgres.image)"
        postgresTag="$(getPomProperty postgres.version)"
        POSTGRES_IMAGE="${postgresStream}:${postgresTag}"
        export POSTGRES_IMAGE
    fi
    compose_files+=("compose/compose-postgres.yaml")
fi

CRYOSTAT_LIVENESS_PATH="${protocol}://cryostat:8181/health/liveness"
export CRYOSTAT_LIVENESS_PATH

if [ -z "$KEYSTORE_PATH" ] && [ -f "$(dirname "$0")/certs/cryostat-keystore.p12" ] ; then
    export KEYSTORE_PATH="/certs/cryostat-keystore.p12"
    KEYSTORE_PASS="$(cat "$(dirname "$0")"/certs/keystore.pass)"
    export KEYSTORE_PASS
fi

child_process() {
    while true; do
        sleep 10
        echo "Scaling down scaled-app..."
        if [ "$CONTAINER_ENGINE" = "podman" ]; then
            podman-compose kill scaled-app
        else 
            docker compose scale scaled-app=0
        fi 

        sleep 5

        echo "Stopping stopped-app..."
        $COMPOSE_ENGINE stop stopped-app

        sleep 10

        echo "Scaling up scaled-app..."
        if [ "$CONTAINER_ENGINE" = "podman" ]; then
            podman-compose restart scaled-app
        else 
            docker compose scale scaled-app=1
        fi

        sleep 5

        echo "Restarting stopped-app..."
        $COMPOSE_ENGINE restart stopped-app
    done
}

# options post-handling

# testing automated rule
if [ "$CT_EN_RULES" = true ]; then
    # generate automated-rule that matches vertx-fib-demos
    touch "$(dirname "$0")"/cryostat-compose-test.json
    echo "{\"name\":\"cryostat-compose-test\",\"description\":\"\",\"matchExpression\":\"target.alias == 'es.andrewazor.demo.Main'\",\"eventSpecifier\":\"template=Continuous,type=TARGET\",\"archivalPeriodSeconds\":30,\"initialDelaySeconds\":30,\"preservedArchives\":3,\"maxAgeSeconds\":35,\"maxSizeBytes\":0,\"enabled\":true}" >> "$(dirname "$0")"/cryostat-compose-test.json
    # move to conf.d
    mv "$(dirname "$0")"/cryostat-compose-test.json "$(dirname "$0")"/conf/rules/cryostat-compose-test.json
else 
    # delete that file if it exists
    rm -f "$(dirname "$0")"/conf/rules/cryostat-compose-test.json
fi

# FIXME: podman-compose does not support COMPOSE_PROFILES yet https://github.com/containers/podman-compose/pull/592
# testing invalid targets

PROFILE_ARGS=""
if [ "$CT_EN_SCALING" = true ]; then
    PROFILE_ARGS+="--profile scaled "
fi

if [ "$CT_EN_INVALID" = true ]; then
    # COMPOSE_PROFILES+=(",invalid")
    PROFILE_ARGS+="--profile invalid "
fi

# testing duplicate Cryostat instance
if [ "$CT_EN_DUPLICATE" = true ]; then
    # COMPOSE_PROFILES+=(",duplicate")
    PROFILE_ARGS+="--profile duplicate "
fi

# trap on CTRL+C SIGINT (we don't want restart signals to tear down the test environment)
trap compose_down INT TERM

merged_yaml="---
services:"

sections=("vertx-jmx" "quarkus-agent")
replicas_variables=("CT_JMX_REPLICAS" "CT_AGENT_REPLICAS")

non_zero_replicas=false

# Iterate over the sections
for ((i=0; i<${#sections[@]}; i++))
do
    section="${sections[i]}"
    replicas_var="${replicas_variables[i]}"
    replicas="${!replicas_var}"

    if (( replicas > 0 )); then
        non_zero_replicas=true

        for ((j=1; j<=replicas; j++))
        do
            current_yaml="$(sed "s/$section/$section-${j}/g" "compose/compose-$section.yaml")"
            current_yaml="${current_yaml//---/}"; current_yaml="${current_yaml//services:/}"
            merged_yaml+="$current_yaml"
        done
    fi
done

if $non_zero_replicas; then
    trap cleanup EXIT
    tmp_yaml=$(mktemp)
    echo "$merged_yaml" >> "$tmp_yaml"
    compose_files+=("$tmp_yaml")
fi

# handle compose_files arguments
COMPOSE_ARGS=""
for file in "${compose_files[@]}"; do
    COMPOSE_ARGS+=" -f $file"
done

$COMPOSE_ENGINE $PROFILE_ARGS $COMPOSE_ARGS up -d --remove-orphans

# testing periodically scaling
if [ "$CT_EN_SCALING" = true ]; then
    child_process &
    child_pid=$!
fi

# handle cryostat container restart signals
wait_on_cryostat() {
    timeout_duration=20
    polling_interval=1
    if timeout "$timeout_duration" bash -c "
        while [ \$($CONTAINER_ENGINE inspect cryostat --format='{{.State.ExitCode}}' 2>/dev/null || echo 1) -ne 0 ] ; do
            echo \"Waiting for cryostat to restart...\"
            sleep $polling_interval
        done
    "; then
        loop
    else
        echo "Cryostat timed out or failed..."
        exit 1
    fi
}

loop() {
    $COMPOSE_ENGINE logs --tail 100 -f cryostat 2>&1 | tee cryostat-run.log
    wait_on_cryostat
}

loop
