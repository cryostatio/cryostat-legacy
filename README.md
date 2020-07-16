# Container-JFR

[![Quay Repository](https://quay.io/repository/rh-jmc-team/container-jfr/status "Quay Repository")](https://quay.io/repository/rh-jmc-team/container-jfr)

## SEE ALSO

See [container-jfr-core](https://github.com/rh-jmc-team/container-jfr-core) for
the core library providing a convenience wrapper and headless stubs for use of
JFR using JDK Mission Control internals.

See
[container-jmc-pubsub-demo](https://github.com/andrewazores/container-jmc-pubsub-demo)
and
[container-jmc-simple-demo](https://github.com/andrewazores/container-jmc-simple-demo)
for multi-container demos of this project.

## REQUIREMENTS
Build:
- Git
- JDK11+
- Maven 3+
- Podman

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
`mvn exec:exec@start-container failsafe:integration-test
exec:exec@stop-container`.

An OCI image can be built to your local `podman` image registry using
`mvn package`. This will normally be a full-fledged image including built
web-client assets. To skip building the web-client and not include its assets
in the OCI image, use `mvn -Dcontainerjfr.minimal=true clean package`. The
`clean` phase should always be specified here, or else previously-generated
client assets will still be included into the built image.

To use other OCI builders, use the `imageBuilder` Maven property, ex.
`mvn -DimageBuilder=$(which docker) clean verify` to build to Docker instead of
Podman.

## RUN
For a basic development non-containerized smoketest, use
`MAVEN_OPTS="-Dcom.sun.management.jmxremote.port=9091 -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.authenticate=false" mvn clean prepare-package exec:java`.

For a Kubernetes/OpenShift deployment, see [container-jfr-operator](https://github.com/rh-jmc-team/container-jfr-operator).
This will deploy container-jfr into your configured cluster in interactive
WebSocket mode with a web frontend.

The `run.sh` script can be used to spin up a `podman` container of the Container
JFR Client, running alone but set up so that it is able to introspect itself
with JFR. This can be achieved by doing `sh run.sh -it` and then typing
`connect localhost` into the client shell that appears. When running in
this container, all three execution modes described above are still available
and accessible using the same mthods. Some client shell demo scripts are also
available in the `demos` directory. These can be used with batch mode, ex.
`sh run.sh "$(more demos/print_help)"`.

There are six network-related environment variables that the client checks
during its runtime:
`CONTAINER_JFR_WEB_HOST`, `CONTAINER_JFR_WEB_PORT`,
`CONTAINER_JFR_EXT_WEB_PORT`, `CONTAINER_JFR_LISTEN_HOST`,
`CONTAINER_JFR_LISTEN_PORT`, `CONTAINER_JFR_EXT_LISTEN_PORT`, and
`CONTAINER_JFR_LOG_LEVEL`.
The former three are used by the embedded webserver
for controlling the port and hostname used and reported when making recordings
available for export (download). The latter three are used when running the
client in daemon/socket mode and controls the port that the client listens for
connections on and which port is reported should be used for connecting to the
command channel socket. (Note: the WebSocket server always listens on
`CONTAINER_JFR_WEB_PORT` and advertises `CONTAINER_JFR_EXT_WEB_PORT` regardless
of `CONTAINER_JFR_LISTEN_PORT` and `CONTAINER_JFR_EXT_LISTEN_PORT`.) These may
be set by setting the environment variable before invoking the `run.sh` shell
script, or if this script is not used, by using the `-e` environment variable
flag in the `docker` or `podman` command invocation. If the `EXT` variables are
unspecified then they default to the value of their non-EXT counterparts. If
`LISTEN_HOST` is unspecified then it defaults to the value of `WEB_HOST`.

The environment variable `CONTAINER_JFR_MAX_WS_CONNECTIONS` is used to
configure the maximum number of concurrent WebSocket client connections that
will be allowed. If this is not set then the default value is 2. Once the
maximum number of concurrent connections is reached, the server will reject
handshakes for any new incoming connections until a previous connection is
closed. The maximum acceptable value is 64 and the minimum acceptable value is
1. Values outside of this range will be ignored and the default value set
instead.

The environment variable `CONTAINER_JFR_LOG_LEVEL` is used to control the level
of messages which will be printed by the logging facility. Acceptable values are
`OFF`, `ERROR`, `WARN`, `INFO`, `DEBUG`, `TRACE`, and `ALL`.

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

The embedded webserver can be optionally configured to enable low memory
pressure mode. By setting `USE_LOW_MEM_PRESSURE_STREAMING` to any non-empty
value, the webserver uses a single buffer when serving recording download
requests. Enabling this option leaves a constant memory size footprint, but
might also reduce the network throughput.

The environment variable `CONTAINER_JFR_CORS_ORIGIN` can be used to specify 
the origin for CORS. This can be used in development to load a different 
instance of the web-client. See [container-jfr-web](https://github.com/rh-jmc-team/container-jfr-web)
for details.

For an overview of the available commands and their functionalities, see
[this document](COMMANDS.md).

## MONITORING APPLICATIONS
In order for `container-jfr` to be able to monitor JVM application targets the
targets must have RJMX enabled. `container-jfr` has several strategies for
automatic discovery of potential targets. Each strategy will be tested in order
until a working strategy is found.

The primary target discovery mechanism uses the Kubernetes API to list services
and expose all discovered services as potential targets. This is runtime
dynamic, allowing `container-jfr` to discover new services which come online
after `container-jfr`, or to detect when known services disappear later. This
requires the `container-jfr` pod to have authorization to list services within
its own namespace.

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
be reachable and in the same subject as `container-jfr`. JDP can be enabled by
passing the flag `"-Dcom.sun.management.jmxremote.autodiscovery=true"` when
starting target JVMs; for more configuration options, see
[this document](https://docs.oracle.com/javase/10/management/java-discovery-protocol.htm)
. Once the targets are properly configured, `container-jfr` will automatically
discover their JMX Service URLs, which includes the RJMX port number for that
specific target.

To enable RJMX on port 9091, the following JVM flags should be passed at target
startup:

```
    '-Dcom.sun.management.jmxremote.port=9091',
    '-Dcom.sun.management.jmxremote.ssl=false',
    '-Dcom.sun.management.jmxremote.authenticate=false'
```

The port number 9091 is arbitrary and may be configured to suit individual
deployments, so long as the two `port` properties above match the desired port
number and the deployment network configuration allows connections on the
configured port. As noted above, the final caveat is that in non-Kube
deployments, port 9091 is expected for automatic port-scanning target discovery.

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
over time). The default directory used is `/flightrecordings`, but the environment
variable `CONTAINER_JFR_ARCHIVE_PATH` can be used to specify a different path. To
enable `container-jfr` archive support ensure that the directory specified by
`CONTAINER_JFR_ARCHIVE_PATH` (or `/flightrecordings` if not set) exists and has
appropriate permissions. `container-jfr` will detect the path and enable related
functionality. `run.sh` has an example of a `tmpfs` volume being mounted with the
default path and enabling the archive functionality.

## SECURING COMMUNICATION CHANNELS
`container-jfr` can be optionally configured to secure HTTP and WebSocket
traffics end-to-end with SSL/TLS.

This feature can be enabled by configuring environment variables to points to
a certificate in the file system. One can set `KEYSTORE_PATH` to point to a
`.jks`, `.pfx` or `.p12` certificate file *and* `KEYSTORE_PASS` to the plaintext
password to such a keystore. Alternatively, one can  set `KEY_PATH` to a PEM
encoded key file *and* `CERT_PATH` to a PEM encoded certificate file.

In the absence of these environment variables, `container-jfr` will look for a
certificate at following locations, in an orderly fashion:

- `$HOME/container-jfr-keystore.jks` (used together with `KEYSTORE_PASS`)
- `$HOME/container-jfr-keystore.pfx` (used together with `KEYSTORE_PASS`)
- `$HOME/container-jfr-keystore.p12` (used together with `KEYSTORE_PASS`)
- `$HOME/container-jfr-key.pem` and `$HOME/container-jfr-cert.pem`

If no certificate can be found, `container-jfr` will fallback to plain
unencrypted `http://` and `ws://` connections.

In case `container-jfr` is deployed behind an SSL proxy, set the environment
variable `CONTAINER_JFR_SSL_PROXIED` to a non-empty value. This informs
`container-jfr` that the URLs it reports pointing back to itself should use
the secure variants of protocols.

If the certificate used for SSL-enabled Grafana/jfr-datasource connections is
self-signed or otherwise untrusted, set the environment variable
`CONTAINER_JFR_ALLOW_UNTRUSTED_SSL` to permit uploads of recordings.

## USER AUTHENTICATION / AUTHORIZATION

ContainerJFR has multiple authz manager implementations for handling user
authentication and authorization against various platforms and mechanisms. This
can be controlled using an environment variable (see the `RUN` section above),
or automatically using platform detection.

In all scenarios, the presence of an auth manager (other than
NoopAuthManager) causes ContainerJFR to expect a token or credentials on command
channel WebSocket messages via a `Sec-WebSocket-Protocol` header , as well as
an `Authorization` header on recording download and report requests.

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
