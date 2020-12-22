# Container-JFR

[![Quay Repository](https://quay.io/repository/rh-jmc-team/container-jfr/status "Quay Repository")](https://quay.io/repository/rh-jmc-team/container-jfr)

## SEE ALSO

* [container-jfr-core](https://github.com/rh-jmc-team/container-jfr-core) for
the core library providing a convenience wrapper and headless stubs for use of
JFR using JDK Mission Control internals.

* [container-jfr-operator](https://github.com/rh-jmc-team/container-jfr-operator)
for an OpenShift Operator facilitating easy setup of ContainerJFR in your OpenShift
cluster as well as exposing the ContainerJFR API as Kubernetes Custom Resources.

* [container-jfr-web](https://github.com/rh-jmc-team/container-jfr) for the React
graphical frontend included as a submodule in ContainerJFR and built into
ContainerJFR's (non-minimal mode) OCI images.

* [JDK Mission Control](https://github.com/openjdk/jmc) for the original JDK
Mission Control, which is the desktop application complement to JFR. Some parts
of JMC are borrowed and re-used to form the basis of ContainerJFR. JMC is still
a recommended tool for more full-featured analysis of JFR files beyond what
ContainerJFR currently implements.

## REQUIREMENTS
Build:
- Git
- JDK11+
- Maven 3+
- Podman 2.0+

Run:
- Kubernetes/OpenShift/Minishift, Podman/Docker, or other container platform

## BUILD
[container-jfr-core](https://github.com/rh-jmc-team/container-jfr-core) is a
required dependency, which is not currently published in an artefact repository
and so must be built and installed into the Maven local repository.
Instructions for doing so are available at that project's README.

Once the `container-jfr-core` local dependency is made available,
`mvn compile` will build the project.

Submodules must be initialized via `git submodule init && git submodule update`.

Unit tests can be run with `mvn test`. Integration tests and additional quality
tools can be run with `mvn verify`.

To re-run integration tests without a rebuild, do
`mvn exec:exec@start-container exec:exec@wait-for-container
failsafe:integration-test exec:exec@stop-container`.

The application OCI image is built on top of a custom base image, built in the
`base-image` directory. To produce a new base image simply run
`sh base-image/build.sh`. This will default to using `podman` to build, which
can be overriden by setting the environment variable `BUILDER` to another
OCI-compliant image builder. The tag and version of the base image can also be
overriden using the `IMAGE` and `TAG` environment variables.

An application OCI image can be built to your local `podman` image registry
using `mvn package`. This will normally be a full-fledged image including built
web-client assets. To skip building the web-client and not include its assets
in the OCI image, use `mvn -Dcontainerjfr.minimal=true clean package`. The
`clean` phase should always be specified here, or else previously-generated
client assets will still be included into the built image. To use other OCI
builders, use the `imageBuilder` Maven property, ex.
`mvn -DimageBuilder=$(which docker) clean verify` to build to Docker instead of
Podman.

## RUN
For a basic development non-containerized smoketest, use
`MAVEN_OPTS="-Dcom.sun.management.jmxremote.port=9091 -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.autodiscovery=true" mvn clean prepare-package exec:java`.

For a Kubernetes/OpenShift deployment, see [container-jfr-operator](https://github.com/rh-jmc-team/container-jfr-operator).
This will deploy container-jfr into your configured cluster.

The `run.sh` script can be used to spin up a `podman` container of the Container
JFR Client, running alone but set up so that it is able to introspect itself
with JFR. This can be achieved by running `sh run.sh` and connecting to
Container JFR in a separate terminal using `curl` or a similar tool to interact
with ContainerJFR via its HTTP(S) API. See [HTTP_API.md](HTTP_API.md).

`smoketest.sh` builds upon `run.sh` and also deploys Grafana, jfr-datasource,
and vertx-fib-demo as a sample app alongside ContainerJFR.

There are three webserver-related environment variables that the client checks
during its runtime:
`CONTAINER_JFR_WEB_HOST`, `CONTAINER_JFR_WEB_PORT`,
`CONTAINER_JFR_EXT_WEB_PORT`.
These are used by the embedded webserver for controlling the port and hostname
used and reported when making recordings and automated reports available for
export (download). These affect both the HTTP(S) API as well as the WebSocket
command channel which runs overtop of the same webserver.

These may be set by setting the environment variable before invoking the
`run.sh` shell script, or if this script is not used, by using the `-e`
environment variable flag in the `docker` or `podman` command invocation. If the
`EXT` variables are unspecified then they default to the value of their non-EXT
counterparts.

The environment variable `CONTAINER_JFR_CORS_ORIGIN` can be used to specify
the origin for CORS. This can be used in development to load a different
instance of the web-client. See [container-jfr-web](https://github.com/rh-jmc-team/container-jfr-web)
for details.

The environment variable `CONTAINER_JFR_MAX_WS_CONNECTIONS` is used to
configure the maximum number of concurrent WebSocket client connections that
will be allowed. If this is not set then the default value is 2. Once the
maximum number of concurrent connections is reached, the server will reject
handshakes for any new incoming connections until a previous connection is
closed. The maximum acceptable value is 64 and the minimum acceptable value is
1\. Values outside of this range will be ignored and the default value set
instead.

The environment variable `CONTAINER_JFR_AUTH_MANAGER` is used to configure which
authentication/authorization manager is used for validating user accesses. See
the `USER AUTHENTICATION / AUTHORIZATION` section for more details. The value
of this variable should be set to the fully-qualified class name of the
auth manager implementation to use, ex.
`com.redhat.rhjmc.containerjfr.net.BasicAuthManager`.

The environment variable `CONTAINER_JFR_PLATFORM` is used to configure which
platform client will be used for performing platform-specific actions, such as
listing available target JVMs. If `CONTAINER_JFR_AUTH_MANAGER` is not specified
then a default auth manager will also be selected corresponding to the platform,
whether that platform is specified by the user or automatically detected. The
value of this variable should be set to the fully-qualified name of the
platform detection strategy implementation to use, ex.
`com.redhat.rhjmc.containerjfr.platform.internal.KubeEnvPlatformStrategy`.

The environment variable `CONTAINER_JFR_REPORT_GENERATION_MAX_HEAP` is used to
configure the maximum heap size used by the container subprocess which forks to
perform automated rules analysis report generation. The default is `200`,
representing a `200MiB` maximum heap size. Too small of a heap size will lead
to report generation failing due to Out-Of-Memory errors. Too large of a heap
size may lead to the subprocess being forcibly killed and the parent process
failing to detect the reason for the failure, leading to inaccurate failure
error messages and API responses.

For logging, Container JFR uses SLF4J with the java.util.logging binding.
The default configuration can be overridden by mounting the desired
configuration file in the container, and setting the environment variable
`CONTAINER_JFR_JUL_CONFIG` to the path of that file.

Some of Container JFR's dependencies also use java.util.logging for their logging.
Container JFR disables
[some of these](https://github.com/rh-jmc-team/container-jfr-core/tree/main/src/main/resources/config/logging.properties)
by default, because they generate unnecessary logs.
However, they can be reenabled by overriding the default configuration file
and setting the disabled loggers to the desired level.

## MONITORING APPLICATIONS
In order for `container-jfr` to be able to monitor JVM application targets the
targets must have RJMX enabled. `container-jfr` has several strategies for
automatic discovery of potential targets. Each strategy will be tested in order
until a working strategy is found.

The primary target discovery mechanism uses the OpenShift/Kubernetes API to list
service endpoints and expose all discovered services as potential targets. This
is runtime dynamic, allowing `container-jfr` to discover new services which come
online after `container-jfr`, or to detect when known services disappear later.
This requires the `container-jfr` pod to have authorization to list services
within its own namespace.

The secondary target discovery mechanism is based on Kubernetes environment
variable service discovery. In this mode, environment variables available to
`container-jfr` (note: environment variables are set once at process creation -
this implies that this method of service discovery is *static* after startup)
are examined for the form `FOO_PORT_1234_TCP_ADDR=127.0.0.1`. Such an
environment variable will cause the discovery of a target at address
`127.0.0.1`, aliased as `foo`, listening on port `1234`.

Finally, if no supported platform is detected, then `container-jfr` will fall
back to the JDP (Java Discovery Protocol) mechanism. This relies on target JVMs
being configured with the JVM flags to enable JDP and requires the targets to
be reachable and in the same subnet as `container-jfr`. JDP can be enabled by
passing the flag `"-Dcom.sun.management.jmxremote.autodiscovery=true"` when
starting target JVMs; for more configuration options, see
[this document](https://docs.oracle.com/javase/10/management/java-discovery-protocol.htm)
. Once the targets are properly configured, `container-jfr` will automatically
discover their JMX Service URLs, which includes the RJMX port number for that
specific target.

To enable RJMX on port 9091, the following JVM flag should be passed at target
startup:

```
    '-Dcom.sun.management.jmxremote.port=9091'
```

The port number 9091 is arbitrary and may be configured to suit individual
deployments, so long as the `port` property above matches the desired port
number and the deployment network configuration allows connections on the
configured port.

Additionally, the following flags are recommended to enable JMX authentication
and connection encryption:

```
-Dcom.sun.management.jmxremote.authenticate=true # enable JMX authentication
-Dcom.sun.management.jmxremote.password.file=/app/resources/jmxremote.password # define users for JMX auth
-Dcom.sun.management.jmxremote.access.file=/app/resources/jmxremote.access # set permissions for JMX users
-Dcom.sun.management.jmxremote.ssl=true # enable JMX SSL
-Dcom.sun.management.jmxremote.registry.ssl=true # enable JMX registry SSL
-Djavax.net.ssl.keyStore=/app/resources/keystore # set your SSL keystore
-Djavax.net.ssl.keyStorePassword=somePassword # set your SSL keystore password
```

## EVENT TEMPLATES

JDK Flight Recorder has event templates, which are preset definition of a set of
events, and for each a set of options and option values. A given JVM is likely
to have some built-in templates ready for use out-of-the-box, but ContainerJFR
also hosts its own small catalog of templates within its own local storage. This
catalog is stored at the path specified by the environment variable
`CONTAINER_JFR_TEMPLATE_PATH`. Templates can be uploaded to ContainerJFR and
then used to create recordings.

## ARCHIVING RECORDINGS

`container-jfr` supports a concept of "archiving" recordings. This simply means
taking the contents of a recording at a point in time and saving these contents
to a file local to the `container-jfr` process (as opposed to "active"
recordings, which exist within the memory of the JVM target and continue to grow
over time). The default directory used is `/flightrecordings`, but the
environment variable `CONTAINER_JFR_ARCHIVE_PATH` can be used to specify a
different path. To enable `container-jfr` archive support ensure that the
directory specified by `CONTAINER_JFR_ARCHIVE_PATH` (or `/flightrecordings` if
not set) exists and has appropriate permissions. `container-jfr` will detect the
path and enable related functionality. `run.sh` has an example of a `tmpfs`
volume being mounted with the default path and enabling the archive
functionality.

## SECURING COMMUNICATION CHANNELS

To specify the SSL certificate for HTTPS/WSS and JMX, one can set
`KEYSTORE_PATH` to point to a `.jks`, `.pfx` or `.p12` certificate file *and*
`KEYSTORE_PASS` to the plaintext password to such a keystore. Alternatively, one
can set `KEY_PATH` to a PEM encoded key file *and* `CERT_PATH` to a PEM encoded
certificate file.

In the absence of these environment variables, `container-jfr` will look for a
certificate at the following locations, in an orderly fashion:

- `$HOME/container-jfr-keystore.jks` (used together with `KEYSTORE_PASS`)
- `$HOME/container-jfr-keystore.pfx` (used together with `KEYSTORE_PASS`)
- `$HOME/container-jfr-keystore.p12` (used together with `KEYSTORE_PASS`)
- `$HOME/container-jfr-key.pem` and `$HOME/container-jfr-cert.pem`

If no certificate can be found, `container-jfr` will autogenerate a self-signed
certificate and use it to secure HTTPS/WSS and JMX connections.

If HTTPS/WSS (SSL) and JMX auth credentials must be disabled then the
environment variables `CONTAINER_JFR_DISABLE_SSL=true` and/or
`CONTAINER_JFR_DISABLE_JMX_AUTH=true` can be set.

In case `container-jfr` is deployed behind an SSL proxy, set the environment
variable `CONTAINER_JFR_SSL_PROXIED` to a non-empty value. This informs
`container-jfr` that the URLs it reports pointing back to itself should use
the secure variants of protocols, even though it itself does not encrypt the
traffic. This is only required if ContainerJFR's own SSL is disabled as above.

If the certificate used for SSL-enabled Grafana/jfr-datasource connections is
self-signed or otherwise untrusted, set the environment variable
`CONTAINER_JFR_ALLOW_UNTRUSTED_SSL` to permit uploads of recordings.

Target JVMs with SSL enabled on JMX connections are also supported. In order to
allow ContainerJFR to establish a connection, the target's certificate must be
copied into ContainerJFR's `/truststore` directory before ContainerJFR's
startup. If ContainerJFR attempts to connect to an SSL-enabled target and no
matching trusted certificate is found then the connection attempt will fail.

## USER AUTHENTICATION / AUTHORIZATION

ContainerJFR has multiple authz manager implementations for handling user
authentication and authorization against various platforms and mechanisms. This
can be controlled using an environment variable (see the `RUN` section above),
or automatically using platform detection.

In all scenarios, the presence of an auth manager (other than
NoopAuthManager) causes ContainerJFR to expect a token or credentials via an
`Authorization` header on all potentially sensitive requests, ex. recording
creations and downloads, report generations.

The OpenShiftPlatformClient.OpenShiftAuthManager uses token authentication.
These tokens are passed through to the OpenShift API for authz and this result
determines whether ContainerJFR accepts the request.

The BasicAuthManager uses basic credential authentication configured with a
standard Java properties file at `$HOME/container-jfr-users.properties`.  The
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
`Sec-WebSocket-Protocol: base64url.bearer.authorization.containerjfr.${base64(TOKEN)}`
WebSocket SubProtocol header.
The token is never stored in any form, only kept in-memory long enough to
process the external token validation.

Basic credentials-based auth managers expect an HTTP
`Authorization: Basic ${base64(user:pass)}` header and a
`Sec-WebSocket-Protocol: basic.authorization.containerjfr.${base64(user:pass)}`
WebSocket SubProtocol header.

If no appropriate auth manager is configured or can be automatically determined
then the fallback is the NoopAuthManager, which does no external validation
calls and simply accepts any provided token or credentials.

## INCOMING JMX CONNECTION AUTHENTICATION

JMX connections into `container-jfr` are secured using the default username
`"containerjfr"` and a randomly generated password.  The environment variables
`CONTAINER_JFR_RJMX_USER` and `CONTAINER_JFR_RJMX_PASS` can be used to override
the default username and specify a password.
