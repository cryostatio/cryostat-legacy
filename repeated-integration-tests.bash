#!/bin/bash

set -o pipefail

failures=0
numeric='^[0-9]+$'
if [[ "$1" =~ $numeric ]]; then
    runs="$1"
else
    runs=1
fi

FLAGS=(
    "exec:exec@create-pod"
    "exec:exec@start-jfr-datasource"
    "exec:exec@start-grafana"
    "exec:exec@start-container"
    "exec:exec@wait-for-container"
    "failsafe:integration-test"
    "failsafe:verify"
    "exec:exec@stop-jfr-datasource"
    "exec:exec@stop-grafana"
    "exec:exec@stop-container"
)

if command -v ansi2txt >/dev/null; then
    FLAGS+=("-Dstyle.color=always")
    PIPECLEANER=ansi2txt
else
    PIPECLEANER=cat
fi

runcount=0
while [ "${runcount}" -lt "${runs}" ]; do
    timestamp="$(date -Iminutes)"
    client_logfile="cryostat-itests-${timestamp}.client.log"
    server_logfile="cryostat-itests-${timestamp}.server.log"
    mvn "${FLAGS[@]}" |& tee >($PIPECLEANER > "${client_logfile}")
    if [ "$?" -ne 0 ]; then
        failures=$((failures+1))
    fi
    runcount=$((runcount+1))
    podman logs cryostat-itest > "${server_logfile}"
    mvn exec:exec@destroy-pod
done

echo
echo "########################"
echo "Test runs completed"
echo "Failures: ${failures}"
echo "Runs: ${runcount}/${runs}"

exit ${failures}
