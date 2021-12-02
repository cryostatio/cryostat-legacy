#!/bin/bash

failures=0
numeric='^[0-9]+$'
if [[ "$1" =~ "${numeric}" ]]; then
    runs="$1"
else
    runs=1
fi

function cleanup() {
    runcount="${runs}"
    exit 1
}
trap cleanup EXIT


FLAGS=(
    "exec:exec@create-pod"
    "exec:exec@start-jfr-datasource"
    "exec:exec@start-grafana"
    "exec:exec@start-container"
    "exec:exec@wait-for-container"
    "failsafe:integration-test"
    "exec:exec@stop-jfr-datasource"
    "exec:exec@stop-grafana"
    "exec:exec@stop-container"
    "exec:exec@destroy-pod"
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
    mvn "${FLAGS[@]}" |& tee >($PIPECLEANER > ${client_logfile})
    if [ "${PIPESTATUS[0]}" -ne 0 ]; then
        failures=$((failures+1))
    fi
    runcount=$((runcount+1))
    podman logs cryostat-itest > "${server_logfile}"
done

echo
echo "########################"
echo "Test runs completed"
echo "Failures: ${failures}"
echo "Runs: ${runs}"
