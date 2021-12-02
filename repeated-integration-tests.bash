#!/bin/bash

set -o pipefail

failures=0
numeric='^[0-9]+$'
if [[ "$1" =~ $numeric ]]; then
    runs="$1"
else
    runs=1
fi

STARTFLAGS=(
    "exec:exec@create-pod"
    "exec:exec@start-jfr-datasource"
    "exec:exec@start-grafana"
    "exec:exec@start-container"
    "exec:exec@wait-for-container"
    "failsafe:integration-test"
    "failsafe:verify"
)

STOPFLAGS=(
    "exec:exec@stop-jfr-datasource"
    "exec:exec@stop-grafana"
    "exec:exec@stop-container"
    "exec:exec@destroy-pod"
)

if command -v ansi2txt >/dev/null; then
    STARTFLAGS+=("-Dstyle.color=always")
    STOPFLAGS+=("-Dstyle.color=always")
    PIPECLEANER=ansi2txt
else
    PIPECLEANER=cat
fi

DIR="$(dirname "$(readlink -f "$0")")"

runcount=0
while [ "${runcount}" -lt "${runs}" ]; do
    timestamp="$(date -Iminutes)"
    client_logfile="$DIR/target/cryostat-itests-${timestamp}.client.log"
    server_logfile="$DIR/target/cryostat-itests-${timestamp}.server.log"
    mvn "${STARTFLAGS[@]}" |& tee -a >($PIPECLEANER > "${client_logfile}")
    if [ "$?" -ne 0 ]; then
        failures=$((failures+1))
    fi
    runcount=$((runcount+1))
    podman logs cryostat-itest > "${server_logfile}"
    mvn "${STOPFLAGS[@]}" |& tee -a >($PIPECLEANER > "${client_logfile}")
done

echo
echo "########################"
echo "Test runs completed"
echo "Failures: ${failures}"
echo "Runs: ${runcount}/${runs}"

exit ${failures}
