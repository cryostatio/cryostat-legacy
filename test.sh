#!/bin/sh

set -x
set -e

getPomProperty() {
    if command -v xpath > /dev/null 2>&1 ; then
        xpath -q -e "project/properties/$1/text()" pom.xml
    elif command -v mvnd > /dev/null 2>&1 ; then
        mvnd help:evaluate -o -B -q -DforceStdout -Dexpression="$1"
    else
        mvn help:evaluate -o -B -q -DforceStdout -Dexpression="$1"
    fi
}

# grafana
DIR="$(dirname "$(readlink -f "$0")")"
host="$(getPomProperty cryostat.itest.webHost)"
# datasourcePort="$(getPomProperty cryostat.itest.jfr-datasource.port)"
# grafanaPort="$(getPomProperty cryostat.itest.grafana.port)"
# export GRAFANA_DATASOURCE_URL="http://${host}:${datasourcePort}"
# export GRAFANA_DASHBOARD_URL="http://${host}:${grafanaPort}"

grafanaStream="$(getPomProperty cryostat.itest.grafana.imageStream)"
grafanaTag="$(getPomProperty cryostat.itest.grafana.version)"
export GRAFANA_IMAGE="${grafanaStream}:${grafanaTag}"
datasourceStream="$(getPomProperty cryostat.itest.jfr-datasource.imageStream)"
datasourceTag="$(getPomProperty cryostat.itest.jfr-datasource.version)"
export DATASOURCE_IMAGE="${datasourceStream}:${datasourceTag}"

# reports
reportsStream="$(getPomProperty cryostat.itest.reports.imageStream)"
reportsTag="$(getPomProperty cryostat.itest.reports.version)"
REPORTS_RJMX_PORT=10000
REPORTS_HTTP_PORT="$(getPomProperty cryostat.itest.reports.port)"
export REPORTS_IMAGE="${reportsStream}:${reportsTag}"
export REPORTS_RJMX_PORT
export REPORTS_HTTP_PORT

# cryostat database
if [ "$1" = "postgres" ]; then
    JDBC_URL="jdbc:postgresql://cryostat:5432/cryostat"
    JDBC_DRIVER="org.postgresql.Driver"
    HIBERNATE_DIALECT="org.hibernate.dialect.PostgreSQL95Dialect"
    JDBC_USERNAME="postgres"
    JDBC_PASSWORD="abcd1234"
    HBM2DDL="update"
elif [ "$1" = "h2mem" ]; then
    JDBC_URL="jdbc:h2:mem:cryostat;DB_CLOSE_DELAY=-1;INIT=create domain if not exists jsonb as varchar"
    JDBC_DRIVER="org.h2.Driver"
    JDBC_USERNAME="cryostat"
    JDBC_PASSWORD=""
    HIBERNATE_DIALECT="org.hibernate.dialect.H2Dialect"
    HBM2DDL="create"
else
    JDBC_URL="jdbc:h2:file:/opt/cryostat.d/conf.d/h2;INIT=create domain if not exists jsonb as varchar"
    JDBC_DRIVER="org.h2.Driver"
    JDBC_USERNAME="cryostat"
    JDBC_PASSWORD=""
    HIBERNATE_DIALECT="org.hibernate.dialect.H2Dialect"
    HBM2DDL="update"
fi

if [ -z "$KEYSTORE_PATH" ] && [ -f "$(dirname "$0")/certs/cryostat-keystore.p12" ] ; then
    export KEYSTORE_PATH="/certs/cryostat-keystore.p12"
    export KEYSTORE_PASS="$(cat "$(dirname "$0")"/certs/keystore.pass)"
fi

export JDBC_URL
export JDBC_DRIVER
export JDBC_USERNAME
export JDBC_PASSWORD
export HIBERNATE_DIALECT
export HBM2DDL

podman-compose up -d

podman-compose logs -t -f --tail 50 cryostat
