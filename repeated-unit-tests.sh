#!/bin/bash

failures=0
numeric='^[0-9]+$'
if [[ "$1" =~ $numeric ]]; then
    runs=$1
else
    runs=50
fi

logfile="cjfr-unittests-$(date -Iminutes).log"

runcount=0
while [ $runcount -lt $runs ]; do
    mvn -Dcontainerjfr.minimal=true surefire:test |& tee -a $logfile
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
