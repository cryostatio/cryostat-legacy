#!/bin/sh

script_dir="$(dirname $(realpath $0))"

if [ -z $MVN ]; then
    MVN=$(which mvn)
fi

for i in archive clientlib conf templates; do
    if [ -e $i ]; then
        if [ ! -d $i ]; then
            echo "$i already exists but is not a directory"
            exit 1
        fi
    else
        mkdir $i
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
    CRYOSTAT_ARCHIVE_PATH="$script_dir/archive" \
    CRYOSTAT_CLIENTLIB_PATH="$script_dir/clientlib" \
    CRYOSTAT_CONFIG_PATH="$script_dir/conf" \
    CRYOSTAT_TEMPLATE_PATH="$script_dir/templates" \
    $MVN vertx:run
