# Cryostat

[![Quay Repository](https://quay.io/repository/cryostat/cryostat/status "Quay Repository")](https://quay.io/repository/cryostat/cryostat)

## SEE ALSO

See [cryostat-core](https://github.com/cryostatio/cryostat-core) for
the core library providing a convenience wrapper and headless stubs for use of
JFR using JDK Mission Control internals.

## REQUIREMENTS
Build:
- Git
- JDK11+
- Maven 3+
- Podman 2.0+

Run:
- Kubernetes/OpenShift/Minishift, Podman/Docker, or other container platform

## BUILD
[cryostat-core](https://github.com/cryostatio/cryostat-core) is a
required dependency, which is not currently published in an artefact repository
and so must be built and installed into the Maven local repository.
Instructions for doing so are available at that project's README.

Once the `cryostat-core` local dependency is made available,
`mvn compile` will build the project.

Submodules must be initialized via `git submodule init && git submodule update`.

Unit tests can be run with `mvn test`. Integration tests and additional quality
tools can be run with `mvn verify`. `-DskipUTs=true` can be used to skip unit
tests and `-DskipITs=true` can be used to skip integration tests;
`-DskipTests=true` can be used to skip both.

To re-run integration tests without a rebuild, do
`mvn exec:exec@create-pod exec:exec@start-jfr-datasource
exec:exec@start-grafana-dashboard exec:exec@start-container
exec:exec@wait-for-container failsafe:integration-test
exec:exec@stop-jfr-datasource exec:exec@stop-grafana exec:exec@stop-container
exec:exec@destroy-pod`, or `bash repeated-integration-tests.sh 1`.

An OCI image can be built to your local `podman` image registry using
`mvn package`. This will normally be a full-fledged image including built
web-client assets. To skip building the web-client and not include its assets
in the OCI image, use `mvn -Dcryostat.minimal=true clean package`. The
`clean` phase should always be specified here, or else previously-generated
client assets will still be included into the built image.

To use other OCI builders, use the `imageBuilder` Maven property, ex.
`mvn -DimageBuilder=$(which docker) clean verify` to build to Docker instead of
Podman.

## RUN
For a basic development non-containerized smoketest, use
`MAVEN_OPTS="-Dcom.sun.management.jmxremote.port=9091 -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.autodiscovery=true" mvn clean prepare-package exec:java`.

For a Kubernetes/OpenShift deployment, see [cryostat-operator](https://github.com/cryostatio/cryostat-operator).
This will deploy cryostat into your configured cluster in interactive
WebSocket mode with a web frontend.

The `run.sh` script can be used to spin up a `podman` container of the Container
JFR Client, running alone but set up so that it is able to introspect itself
with JFR. This can be achieved by running `sh run.sh` and connecting to
Cryostat in a separate terminal using
[websocat](https://github.com/vi/websocat). The WebSocket URL to connect to can
be found by running `curl localhost:8181/api/v1/clienturl`. Once you are
connected, you can issue commands by entering them into the websocat client in
JSON form. For example, `{command:ping}` or
`{command:dump,args:[localhost,foo,10,"template=Continuous"]}`. See
[COMMANDS.md](COMMANDS.md) for a description of the Command Channel API. `curl`
or a similar tool may also be used to interact with Cryostat via its HTTP(S)
API. See [HTTP_API.md](HTTP_API.md).

`smoketest.sh` builds upon `run.sh` and also deploys Grafana, jfr-datasource,
and vertx-fib-demo as a sample app alongside Cryostat.

There are three webserver-related environment variables that the client checks
during its runtime:
`CRYOSTAT_WEB_HOST`, `CRYOSTAT_WEB_PORT`,
`CRYOSTAT_EXT_WEB_PORT`.
These are used by the embedded webserver for controlling the port and hostname
used and reported when making recordings and automated reports available for
export (download). These affect both the HTTP(S) API as well as the WebSocket
command channel which runs overtop of the same webserver.

The environment variable `CRYOSTAT_MAX_WS_CONNECTIONS` is used to
configure the maximum number of concurrent WebSocket client connections that
will be allowed. If this is not set then the default value is 2. Once the
maximum number of concurrent connections is reached, the server will reject
handshakes for any new incoming connections until a previous connection is
closed. The maximum acceptable value is 64 and the minimum acceptable value is
1\. Values outside of this range will be ignored and the default value set
instead.

The environment variable `CRYOSTAT_AUTH_MANAGER` is used to configure which
authentication/authorization manager is used for validating user accesses. See
the `USER AUTHENTICATION / AUTHORIZATION` section for more details. The value
of this variable should be set to the fully-qualified class name of the
auth manager implementation to use, ex.
`BasicAuthManager`.

The environment variable `CRYOSTAT_PLATFORM` is used to configure which
platform client will be used for performing platform-specific actions, such as
listing available target JVMs. If `CRYOSTAT_AUTH_MANAGER` is not specified
then a default auth manager will also be selected corresponding to the platform,
whether that platform is specified by the user or automatically detected. The
value of this variable should be set to the fully-qualified name of the
platform detection strategy implementation to use, ex.
`KubeEnvPlatformStrategy`.

The environment variable `CRYOSTAT_REPORT_GENERATION_MAX_HEAP` is used to
configure the maximum heap size used by the container subprocess which forks to
perform automated rules analysis report generation. The default is `200`,
representing a `200MiB` maximum heap size. Too small of a heap size will lead
to report generation failing due to Out-Of-Memory errors. Too large of a heap
size may lead to the subprocess being forcibly killed and the parent process
failing to detect the reason for the failure, leading to inaccurate failure
error messages and API responses.

The environment variable `CRYOSTAT_CORS_ORIGIN` can be used to specify
the origin for CORS. This can be used in development to load a different
instance of the web-client. See [cryostat-web](https://github.com/cryostatio/cryostat-web)
for details.

For logging, Cryostat uses SLF4J with the java.util.logging binding.
The default configuration can be overridden by mounting the desired
configuration file in the container, and setting the environment variable
`CRYOSTAT_JUL_CONFIG` to the path of that file.

Some of Cryostat's dependencies also use java.util.logging for their logging.
Cryostat disables
[some of these](https://github.com/cryostatio/cryostat-core/tree/main/src/main/resources/config/logging.properties)
by default, because they generate unnecessary logs.
However, they can be reenabled by overriding the default configuration file
and setting the disabled loggers to the desired level.

## MONITORING APPLICATIONS
In order for `cryostat` to be able to monitor JVM application targets the
targets must have RJMX enabled. `cryostat` has several strategies for
automatic discovery of potential targets. Each strategy will be tested in order
until a working strategy is found.

The primary target discovery mechanism uses the Kubernetes API to list services
and expose all discovered services as potential targets. This is runtime
dynamic, allowing `cryostat` to discover new services which come online
after `cryostat`, or to detect when known services disappear later. This
requires the `cryostat` pod to have authorization to list services within
its own namespace.

The secondary target discovery mechanism is based on Kubernetes environment
variable service discovery. In this mode, environment variables available to
`cryostat` (note: environment variables are set once at process creation -
this implies that this method of service discovery is *static* after startup)
are examined for the form `FOO_PORT_1234_TCP_ADDR=127.0.0.1`. Such an
environment variable will cause the discovery of a target at address
`127.0.0.1`, aliased as `foo`, listening on port `1234`.

Finally, if no supported platform is detected, then `cryostat` will fall
back to the JDP (Java Discovery Protocol) mechanism. This relies on target JVMs
being configured with the JVM flags to enable JDP and requires the targets to
be reachable and in the same subnet as `cryostat`. JDP can be enabled by
passing the flag `"-Dcom.sun.management.jmxremote.autodiscovery=true"` when
starting target JVMs; for more configuration options, see
[this document](https://docs.oracle.com/javase/10/management/java-discovery-protocol.htm)
. Once the targets are properly configured, `cryostat` will automatically
discover their JMX Service URLs, which includes the RJMX port number for that
specific target.

To enable RJMX on port 9091, the following JVM flag should be passed at target
startup:

```
    '-Dcom.sun.management.jmxremote.port=9091',
```

The port number 9091 is arbitrary and may be configured to suit individual
deployments, so long as the two `port` properties above match the desired port
number and the deployment network configuration allows connections on the
configured port.

## EVENT TEMPLATES

JDK Flight Recorder has event templates, which are preset definition of a set of
events, and for each a set of options and option values. A given JVM is likely
to have some built-in templates ready for use out-of-the-box, but Cryostat
also hosts its own small catalog of templates within its own local storage. This
catalog is stored at the path specified by the environment variable
`CRYOSTAT_TEMPLATE_PATH`. Templates can be uploaded to Cryostat and
then used to create recordings.

## ARCHIVING RECORDINGS

`cryostat` supports a concept of "archiving" recordings. This simply means
taking the contents of a recording at a point in time and saving these contents
to a file local to the `cryostat` process (as opposed to "active"
recordings, which exist within the memory of the JVM target and continue to grow
over time). The default directory used is `/flightrecordings`, but the
environment variable `CRYOSTAT_ARCHIVE_PATH` can be used to specify a
different path. To enable `cryostat` archive support ensure that the
directory specified by `CRYOSTAT_ARCHIVE_PATH` (or `/flightrecordings` if
not set) exists and has appropriate permissions. `cryostat` will detect the
path and enable related functionality. `run.sh` has an example of a `tmpfs`
volume being mounted with the default path and enabling the archive
functionality.

## SECURING COMMUNICATION CHANNELS

To specify the SSL certificate for HTTPS/WSS and JMX, one can set
`KEYSTORE_PATH` to point to a `.jks`, `.pfx` or `.p12` certificate file *and*
`KEYSTORE_PASS` to the plaintext password to such a keystore. Alternatively, one
can set `KEY_PATH` to a PEM encoded key file *and* `CERT_PATH` to a PEM encoded
certificate file.

In the absence of these environment variables, `cryostat` will look for a
certificate at the following locations, in an orderly fashion:

- `$HOME/cryostat-keystore.jks` (used together with `KEYSTORE_PASS`)
- `$HOME/cryostat-keystore.pfx` (used together with `KEYSTORE_PASS`)
- `$HOME/cryostat-keystore.p12` (used together with `KEYSTORE_PASS`)
- `$HOME/cryostat-key.pem` and `$HOME/cryostat-cert.pem`

If no certificate can be found, `cryostat` will autogenerate a self-signed
certificate and use it to secure HTTPS/WSS and JMX connections.

If HTTPS/WSS (SSL) and JMX auth credentials must be disabled then the
environment variables `CRYOSTAT_DISABLE_SSL=true` and/or
`CRYOSTAT_DISABLE_JMX_AUTH=true` can be set.

In case `cryostat` is deployed behind an SSL proxy, set the environment
variable `CRYOSTAT_SSL_PROXIED` to a non-empty value. This informs
`cryostat` that the URLs it reports pointing back to itself should use
the secure variants of protocols, even though it itself does not encrypt the
traffic. This is only required if Cryostat's own SSL is disabled as above.

If the certificate used for SSL-enabled Grafana/jfr-datasource connections is
self-signed or otherwise untrusted, set the environment variable
`CRYOSTAT_ALLOW_UNTRUSTED_SSL` to permit uploads of recordings.

Target JVMs with SSL enabled on JMX connections are also supported. In order to
allow Cryostat to establish a connection, the target's certificate must be
copied into Cryostat's `/truststore` directory before Cryostat's
startup. If Cryostat attempts to connect to an SSL-enabled target and no
matching trusted certificate is found then the connection attempt will fail.

## USER AUTHENTICATION / AUTHORIZATION

Cryostat has multiple authz manager implementations for handling user
authentication and authorization against various platforms and mechanisms. This
can be controlled using an environment variable (see the `RUN` section above),
or automatically using platform detection.

In all scenarios, the presence of an auth manager (other than
NoopAuthManager) causes Cryostat to expect a token or credentials on command
channel WebSocket messages via a `Sec-WebSocket-Protocol` header , as well as
an `Authorization` header on recording download and report requests.

The OpenShiftPlatformClient.OpenShiftAuthManager uses token authentication.
These tokens are passed through to the OpenShift API for authz and this result
determines whether Cryostat accepts the request.

The BasicAuthManager uses basic credential authentication configured with a
standard Java properties file at `$HOME/cryostat-users.properties`.  The
credentials stored in the Java properties file are the user name and a SHA-256
sum hex of the user's password. The property file contents should look like:
```
user1=abc123
user2=def987
```
Where `abc123` and `def987` are substituted for the SHA-256 sum hexes of the
desired user passwords. These can be obtained by ex.
`echo -n PASS | sha256sum | cut -d' ' -f1'`.

Token-based auth managers expect an HTTP `Authorization: Bearer TOKEN` header
and a
`Sec-WebSocket-Protocol: base64url.bearer.authorization.cryostat.${base64(TOKEN)}`
WebSocket SubProtocol header.
The token is never stored in any form, only kept in-memory long enough to
process the external token validation.

Basic credentials-based auth managers expect an HTTP
`Authorization: Basic ${base64(user:pass)}` header and a
`Sec-WebSocket-Protocol: basic.authorization.cryostat.${base64(user:pass)}`
WebSocket SubProtocol header.

If no appropriate auth manager is configured or can be automatically determined
then the fallback is the NoopAuthManager, which does no external validation
calls and simply accepts any provided token or credentials.

## INCOMING JMX CONNECTION AUTHENTICATION

JMX connections into `cryostat` are secured using the default username
`"cryostat"` and a randomly generated password.  The environment variables
`CRYOSTAT_RJMX_USER` and `CRYOSTAT_RJMX_PASS` can be used to override
the default username and specify a password.
