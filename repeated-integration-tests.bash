#!/bin/bash
# shellcheck disable=SC2188

set -o pipefail

failures=0
numeric='^[0-9]+$'
if [[ "$1" =~ $numeric ]]; then
    runs="$1"
else
    runs=1
fi

if [ -z "${MVN}" ]; then
    MVN="$(which mvn)"
fi

getPomProperty() {
    if command -v xpath > /dev/null 2>&1 ; then
        xpath -q -e "project/$1/text()" pom.xml
    else
        "${MVN}" help:help > /dev/null 2>&1
        "${MVN}" build-helper:regex-property@image-tag-to-lower help:evaluate -o -B -q -DforceStdout -Dexpression="$1"
    fi
}

if [ -z "${POD_NAME}" ]; then
    POD_NAME="$(getPomProperty properties/cryostat.itest.podName)"
fi

if [ -z "${CONTAINER_NAME}" ]; then
    CONTAINER_NAME="$(getPomProperty properties/cryostat.itest.containerName)"
fi

if [ -z "${ITEST_IMG_VERSION}" ]; then
    ITEST_IMG_VERSION="latest"
fi

if [ -z "${PULL_IMAGES}" ]; then
    PULL_IMAGES="always"
fi

STARTFLAGS=(
    "-DfailIfNoTests=true"
    "-Dcryostat.itest.imageTag=${ITEST_IMG_VERSION}"
    "-Dcryostat.itest.pullImages=${PULL_IMAGES}"
    "build-helper:regex-property@image-tag-to-lower"
    "exec:exec@create-pod"
    "exec:exec@start-jfr-datasource"
    "exec:exec@start-grafana"
    "exec:exec@start-cryostat"
    "exec:exec@wait-for-jfr-datasource"
    "exec:exec@wait-for-grafana"
    "exec:exec@wait-for-cryostat"
    "failsafe:integration-test"
    "failsafe:verify"
    "exec:exec@capture-oci-logs"
)

if [ -n "$2" ]; then
    STARTFLAGS+=("-Dit.test=$2")
fi

STOPFLAGS=(
    "exec:exec@destroy-pod"
)

if command -v ansi2txt >/dev/null; then
    STARTFLAGS+=("-Dstyle.color=always")
    STOPFLAGS+=("-Dstyle.color=always")
    PIPECLEANER="ansi2txt"
else
    PIPECLEANER="cat"
fi

function cleanup() {
    if podman pod exists "${POD_NAME}"; then
        "${MVN}" "${STOPFLAGS[@]}"
    fi
}
trap cleanup EXIT
cleanup

DIR="$(dirname "$(readlink -f "$0")")"

"${MVN}" -Dheadless=true test-compile

runcount=0
while [ "${runcount}" -lt "${runs}" ]; do
    timestamp="$(date -Iminutes)"
    client_logfile="$DIR/target/${CONTAINER_NAME}-${timestamp}.client.log"
    server_logfile="$DIR/target/${CONTAINER_NAME}-${timestamp}.server.log"
    mkdir -p "$(dirname "$client_logfile")"
    mkdir -p "$(dirname "$server_logfile")"
    >"${client_logfile}"
    >"${server_logfile}"
    if ! "${MVN}" "${STARTFLAGS[@]}" |& tee -a >($PIPECLEANER >> "${client_logfile}"); then
        failures=$((failures+1))
    fi
    runcount=$((runcount+1))
    podman pod logs -c "${CONTAINER_NAME}" "${POD_NAME}" &>> "${server_logfile}"
    "${MVN}" "${STOPFLAGS[@]}" |& tee -a >($PIPECLEANER >> "${client_logfile}")
done

echo
echo "########################"
echo "Test runs completed"
echo "Failures: ${failures}"
echo "Runs: ${runcount}/${runs}"

exit ${failures}
