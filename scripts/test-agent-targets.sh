
parent_dir="$(dirname "$(dirname "$(readlink -fm "$0")")")"
"$parent_dir/smoketest.sh"


runDemoApps() {
    podman run \
        --name vertx-fib-demo-1 \
        --env HTTP_PORT=8081 \
        --env JMX_PORT=9093 \
        --pod cryostat-pod \
        --label io.cryostat.connectUrl="service:jmx:rmi:///jndi/rmi://localhost:9093/jmxrmi" \
        --rm -d quay.io/andrewazores/vertx-fib-demo:0.9.1

    podman run \
        --name vertx-fib-demo-2 \
        --env HTTP_PORT=8082 \
        --env JMX_PORT=9094 \
        --env USE_AUTH=true \
        --pod cryostat-pod \
        --label io.cryostat.connectUrl="service:jmx:rmi:///jndi/rmi://localhost:9094/jmxrmi" \
        --rm -d quay.io/andrewazores/vertx-fib-demo:0.9.1

    podman run \
        --name vertx-fib-demo-3 \
        --env HTTP_PORT=8083 \
        --env JMX_PORT=9095 \
        --env USE_SSL=true \
        --env USE_AUTH=true \
        --pod cryostat-pod \
        --label io.cryostat.connectUrl="service:jmx:rmi:///jndi/rmi://localhost:9095/jmxrmi" \
        --rm -d quay.io/andrewazores/vertx-fib-demo:0.9.1

    local webPort;
    if [ -z "$CRYOSTAT_WEB_PORT" ]; then
        webPort="$(getPomProperty cryostat.itest.webPort)"
    else
        webPort="${CRYOSTAT_WEB_PORT}"
    fi
    if [ -z "$CRYOSTAT_DISABLE_SSL" ]; then
        local protocol="https"
    else
        local protocol="http"
    fi

    # this config is broken on purpose (missing required env vars) to test the agent's behaviour
    # when not properly set up
    podman run \
        --name quarkus-test-agent-0 \
        --pod cryostat-pod \
        --env JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager -javaagent:/deployments/app/cryostat-agent.jar" \
        --env QUARKUS_HTTP_PORT=10009 \
        --env ORG_ACME_CRYOSTATSERVICE_ENABLED="false" \
        --env CRYOSTAT_AGENT_WEBCLIENT_SSL_TRUST_ALL="true" \
        --env CRYOSTAT_AGENT_WEBCLIENT_SSL_VERIFY_HOSTNAME="false" \
        --rm -d quay.io/andrewazores/quarkus-test:latest

    podman run \
        --name quarkus-test-agent-1 \
        --pod cryostat-pod \
        --env JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager -Dcom.sun.management.jmxremote.port=9097 -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.authenticate=false -javaagent:/deployments/app/cryostat-agent.jar" \
        --env QUARKUS_HTTP_PORT=10010 \
        --env ORG_ACME_CRYOSTATSERVICE_ENABLED="false" \
        --env CRYOSTAT_AGENT_APP_NAME="quarkus-test-agent" \
        --env CRYOSTAT_AGENT_WEBCLIENT_SSL_TRUST_ALL="true" \
        --env CRYOSTAT_AGENT_WEBCLIENT_SSL_VERIFY_HOSTNAME="false" \
        --env CRYOSTAT_AGENT_WEBSERVER_HOST="localhost" \
        --env CRYOSTAT_AGENT_WEBSERVER_PORT="9977" \
        --env CRYOSTAT_AGENT_CALLBACK="http://localhost:9977/" \
        --env CRYOSTAT_AGENT_BASEURI="${protocol}://localhost:${webPort}/" \
        --env CRYOSTAT_AGENT_TRUST_ALL="true" \
        --env CRYOSTAT_AGENT_AUTHORIZATION="Basic $(echo user:pass | base64)" \
        --env CRYOSTAT_AGENT_REGISTRATION_PREFER_JMX="true" \
        --env CRYOSTAT_AGENT_HARVESTER_PERIOD_MS=60000 \
        --env CRYOSTAT_AGENT_HARVESTER_MAX_FILES=10 \
        --rm -d quay.io/andrewazores/quarkus-test:latest

    podman run \
        --name quarkus-test-agent-2 \
        --pod cryostat-pod \
        --env JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager -javaagent:/deployments/app/cryostat-agent.jar" \
        --env QUARKUS_HTTP_PORT=10011 \
        --env ORG_ACME_CRYOSTATSERVICE_ENABLED="false" \
        --env CRYOSTAT_AGENT_APP_NAME="quarkus-test-agent" \
        --env CRYOSTAT_AGENT_WEBCLIENT_SSL_TRUST_ALL="true" \
        --env CRYOSTAT_AGENT_WEBCLIENT_SSL_VERIFY_HOSTNAME="false" \
        --env CRYOSTAT_AGENT_WEBSERVER_HOST="localhost" \
        --env CRYOSTAT_AGENT_WEBSERVER_PORT="9988" \
        --env CRYOSTAT_AGENT_CALLBACK="http://localhost:9988/" \
        --env CRYOSTAT_AGENT_BASEURI="${protocol}://localhost:${webPort}/" \
        --env CRYOSTAT_AGENT_TRUST_ALL="true" \
        --env CRYOSTAT_AGENT_AUTHORIZATION="Basic $(echo user:pass | base64)" \
        --env CRYOSTAT_AGENT_REGISTRATION_PREFER_JMX="true" \
        --rm -d quay.io/andrewazores/quarkus-test:latest
}
