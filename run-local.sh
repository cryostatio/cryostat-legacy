#!/bin/sh

if [ -z $MVN ]; then
    MVN=$(which mvn)
fi

if [ ! -f target/classes/io/cryostat/Cryostat.class ]; then
    echo "Ensure you have done 'mvn prepare-package' before running this script"
    exit 1
fi

script_dir="$(dirname $(realpath $0))"

flags=(
    "-Dvertxweb.environment=dev"
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
    CRYOSTAT_WEB_HOST=$(hostname) \
    CRYOSTAT_WEB_PORT=8080 \
    CRYOSTAT_AUTH_MANAGER=io.cryostat.net.NoopAuthManager \
    CRYOSTAT_ARCHIVE_PATH="$script_dir/archive" \
    CRYOSTAT_CLIENTLIB_PATH="$script_dir/clientlib" \
    CRYOSTAT_CONFIG_PATH="$script_dir/conf" \
    CRYOSTAT_TEMPLATE_PATH="$script_dir/templates" \
    $MVN exec:java
