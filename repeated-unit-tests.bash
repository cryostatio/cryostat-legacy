#!/bin/bash

set -o pipefail

failures=0
numeric='^[0-9]+$'
if [[ "$1" =~ "${numeric}" ]]; then
    runs="$1"
else
    runs=50
fi

FLAGS=(
    "-Dcryostat.minimal=true"
)

if command -v ansi2txt >/dev/null; then
    FLAGS+=("-Dstyle.color=always")
    PIPECLEANER=ansi2txt
else
    PIPECLEANER=cat
fi

runcount=0
while [ "${runcount}" -lt ${runs} ]; do
    logfile="cryostat-unittests-$(date -Iminutes).log"
    mvn "${FLAGS[@]}" surefire:test |& tee >($PIPECLEANER > "${logfile}")
    if [ $? -ne 0 ]; then
        failures=$((failures+1))
    fi
    runcount=$((runcount+1))
done

echo
echo "########################"
echo "Test runs completed"
echo "Failures: ${failures}"
echo "Runs: ${runs}"
