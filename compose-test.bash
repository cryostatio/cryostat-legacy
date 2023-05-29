#!/bin/bash
# shellcheck disable=SC1091,SC2086

set -e

source "$(dirname "$0")/.env"

echo $COMPOSE_FILE

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

display_usage() {
    echo "Usage: $(basename "$0") [-a targets] [-j targets] [-d] [-i] [-r] [-s] [-A]"
    echo "Options:"
    echo "  -a targets  Sets # of agent targets"
    echo "  -j targets  Sets # of JMX targets"
    echo "  -d          Enables Cryostat duplicate"
    echo "  -i          Enables invalid targets"
    echo "  -r          Enables automated rule that matches all targets"
    echo "  -s          Enables periodic target scaling/restarting"
    echo "  -A          Enables all of the above; Sets targets to 10 each"
}


# Parse command-line options
while getopts ":a:j:dirsA" opt; do
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
        r)
            CT_EN_RULES=true
            ;;
        s)
            CT_EN_SCALING=true
            ;;
        A)
            CT_AGENT_REPLICAS=10
            CT_JMX_REPLICAS=10
            CT_EN_DUPLICATE=true
            CT_EN_INVALID=true
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

cleanup() {
    $COMPOSE_ENGINE down --remove-orphans
    if [ -n "$child_pid" ]; then
        kill "$child_pid"
    fi
}

webPort="$(getPomProperty cryostat.itest.webPort)"

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
CRYOSTAT_AGENT_BASEURI="${protocol}://cryostat:${webPort}/"
export CRYOSTAT_AGENT_AUTHORIZATION
export CRYOSTAT_AGENT_BASEURI

# cryostat database
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

if [ -z "$KEYSTORE_PATH" ] && [ -f "$(dirname "$0")/certs/cryostat-keystore.p12" ] ; then
    export KEYSTORE_PATH="/certs/cryostat-keystore.p12"
    KEYSTORE_PASS="$(cat "$(dirname "$0")"/certs/keystore.pass)"
    export KEYSTORE_PASS
fi

export JDBC_URL
export JDBC_DRIVER
export JDBC_USERNAME
export JDBC_PASSWORD
export HIBERNATE_DIALECT
export HBM2DDL

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
trap cleanup INT TERM

# export COMPOSE_PROFILES
export COMPOSE_FILE

### vertx-jmx handling
merged_yaml=""

for ((i=1; i<=CT_JMX_REPLICAS; i++))
do
  current_yaml="$(sed "s/vertx-jmx/vertx-jmx-${i}/g" compose-vertx-jmx.yaml)"
  
  # Remove "services:" from all but the first iteration
  if [[ $i -gt 1 ]]; then
    current_yaml="${current_yaml//services:/}"
  fi
  
  merged_yaml+="$current_yaml"
done

echo "$merged_yaml"

echo "$merged_yaml" | $COMPOSE_ENGINE $PROFILE_ARGS -f compose-cryostat.yaml -f compose-cryostat-reports.yaml -f - up -d --remove-orphans 

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
    $COMPOSE_ENGINE logs -f --tail 50 cryostat 2>/dev/null
    wait_on_cryostat
}

loop

