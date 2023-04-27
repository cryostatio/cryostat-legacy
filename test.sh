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

DIR="$(dirname "$(readlink -f "$0")")"
host="$(getPomProperty cryostat.itest.webHost)"
datasourcePort="$(getPomProperty cryostat.itest.jfr-datasource.port)"
grafanaPort="$(getPomProperty cryostat.itest.grafana.port)"

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

export JDBC_URL
export JDBC_DRIVER
export JDBC_USERNAME
export JDBC_PASSWORD
export HIBERNATE_DIALECT
export HBM2DDL
export KEYSTORE_PASS="$(cat "$(dirname "$0")"/certs/keystore.pass)"
export GRAFANA_DATASOURCE_URL="http://${host}:${datasourcePort}"
export GRAFANA_DASHBOARD_URL="http://${host}:${grafanaPort}"
export CRYOSTAT_REPORT_GENERATOR="http://${host}:10001" \

podman-compose up
