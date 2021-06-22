#!/bin/bash

failures=0
numeric='^[0-9]+$'
if [[ "$1" =~ $numeric ]]; then
    runs=$1
else
    runs=50
fi

function cleanup() {
    runcount=$runs
    exit 1
}
trap cleanup EXIT

logfile="cryostat-itests-$(date -Iminutes).log"

runcount=0
while [ $runcount -lt $runs ]; do
    mvn \
        exec:exec@create-pod \
        exec:exec@start-jfr-datasource \
        exec:exec@start-grafana \
        exec:exec@start-container \
        exec:exec@wait-for-container \
        failsafe:integration-test \
        exec:exec@stop-jfr-datasource \
        exec:exec@stop-grafana \
        exec:exec@stop-container \
        exec:exec@destroy-pod |& tee -a $logfile
    if [ $? -ne 0 ]; then
        failures=$((failures+1))
    fi
    runcount=$((runcount+1))
done

echo
echo "########################"
echo "Test runs completed"
echo "Failures: $failures"
echo "Runs: $runs"
