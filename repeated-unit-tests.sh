#!/bin/bash

failures=0
numeric='^[0-9]+$'
if [[ "$1" =~ $numeric ]]; then
    runs=$1
else
    runs=50
fi

runcount=0
while [ $runcount -lt $runs ]; do
    mvn -Dcontainerjfr.minimal=true test
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
