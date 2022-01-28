#!/bin/sh

work_dir=$(mktemp -d)

if [ -z $MVN ]; then
    MVN=$(which mvn)
fi

for i in archive clientlib conf templates probes; do
    if [ -e $work_dir/$i ]; then
        if [ ! -d $work_dir/$i ]; then
            echo "$work_dir/$i already exists but is not a directory"
            exit 1
        fi
    else
        mkdir $work_dir/$i
    fi
done

flags=(
    "-Dcom.sun.management.jmxremote.autodiscovery=true"
    "-Dcom.sun.management.jmxremote.port=9091"
    "-Dcom.sun.management.jmxremote.rmi.port=9091"
    "-Dcom.sun.management.jmxremote.authenticate=false"
    "-Dcom.sun.management.jmxremote.ssl=false"
    "-Dcom.sun.management.jmxremote.registry.ssl=false"
)

MAVEN_OPTS="${flags[@]}" \
    CRYOSTAT_PLATFORM=io.cryostat.platform.internal.DefaultPlatformStrategy \
    CRYOSTAT_DISABLE_SSL=true \
    CRYOSTAT_DISABLE_JMX_AUTH=true \
    CRYOSTAT_WEB_HOST=localhost \
    CRYOSTAT_WEB_PORT=8181 \
    CRYOSTAT_CORS_ORIGIN=http://localhost:9000 \
    CRYOSTAT_AUTH_MANAGER=io.cryostat.net.NoopAuthManager \
    CRYOSTAT_ARCHIVE_PATH="$work_dir/archive" \
    CRYOSTAT_CLIENTLIB_PATH="$work_dir/clientlib" \
    CRYOSTAT_CONFIG_PATH="$work_dir/conf" \
    CRYOSTAT_TEMPLATE_PATH="$work_dir/templates" \
    CRYOSTAT_PROBE_TEMPLATE_PATH="$work_dir/probes" \
    $MVN -Dcryostat.minimal=true clean vertx:run
