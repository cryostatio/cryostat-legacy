<a target="_blank" href="https://cryostat.io">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="./docs/images/cryostat_logo_hori_rgb_reverse.svg">
    <img src="./docs/images/cryostat_logo_hori_rgb_default.svg">
  </picture>
</a>

[![CI build](https://github.com/cryostatio/cryostat/actions/workflows/push-ci.yml/badge.svg)](https://github.com/cryostatio/cryostat/actions/workflows/push-ci.yml)
[![Quay Repository](https://quay.io/repository/cryostat/cryostat/status "Quay Repository")](https://quay.io/repository/cryostat/cryostat)
[![Google Group : Cryostat Development](https://img.shields.io/badge/Google%20Group-Cryostat%20Development-blue.svg)](https://groups.google.com/g/cryostat-development)

A container-native JVM application which acts as a bridge to other containerized JVMs and exposes a secure API for producing, analyzing, and retrieving JDK Flight Recorder data from your cloud workloads.

## SEE ALSO

* [cryostat.io](https://cryostat.io) : upstream documentation website with user
  guides, tutorials, blog posts, and other user-facing content. Start here if
  what you've read so far sounds interesting and you want to know more as a
  **user**, rather than as a _developer_.

* [cryostat-core](https://github.com/cryostatio/cryostat-core) :
the core library providing a convenience wrapper and headless stubs for use of
JFR using JDK Mission Control internals.

* [cryostat-operator](https://github.com/cryostatio/cryostat-operator)
 : an OpenShift Operator deploying Cryostat in your OpenShift
cluster as well as exposing the Cryostat API as Kubernetes Custom Resources.

* [cryostat-web](https://github.com/cryostatio/cryostat-web) : the React
frontend included as a submodule in Cryostat and built into
Cryostat's (non-headless mode) OCI images.

* [JDK Mission Control](https://github.com/openjdk/jmc) for the original JDK
Mission Control, which is the desktop application complement to JFR. Some parts
of JMC are borrowed and re-used to form the basis of Cryostat. JMC is still
a recommended tool for more full-featured analysis of JFR files beyond what
Cryostat currently implements.

## CONTRIBUTING

We welcome and appreciate any contributions from our community. Please visit our guide on how you can take part in improving Cryostat.

[See contribution guide →](./CONTRIBUTING.md)

## REQUIREMENTS
Build Requirements:
- Git
- JDK17+
- Maven 3+
- Podman 2.0+
- `qemu-user-static` to build container images for other archs

Run Requirements:
- Kubernetes/OpenShift/Minishift, Podman/Docker, or other container platform
- `systemctl --user enable --now podman.socket` to enable the user podman.socket for podman discovery

## BUILD

### Setup Dependencies

* Clone and install [cryostat-core](https://github.com/cryostatio/cryostat-core) via it's [instructions](https://github.com/cryostatio/cryostat-core/blob/main/README.md)
* Initialize submodules via:  `git submodule init && git submodule update`

### Build project locally
* `./mvnw compile`

### Build and run project locally in development hot-reload mode
* `sh devserver.sh` - this will start the Vert.x backend in hot-reload mode, so
any modifications to files in `src/` will cause a re-compilation and re-deploy.
This is only intended for use during development. The `web-client` assets will
not be built and will not be included in the application classpath. To set up
the `web-client` frontend for hot-reload development, see
[cryostat-web Development Server](https://github.com/cryostatio/cryostat-web/blob/main/README.md#development-server).

### Build and push to local podman image registry
* `./mvnw package`
* Run `./mvnw -Dheadless=true clean package` to exclude web-client assets.
The `clean` phase should always be specified here, or else previously-generated
client assets will still be included into the built image.
* For other OCI builders, use the `imageBuilder` Maven property. For example, to
use docker, run: `./mvnw -DimageBuilder=$(which docker) clean verify`

## TEST

### Unit tests
* `./mvnw test`

### Integration tests and analysis tools
* `./mvnw verify`

### Skipping tests
* `-DskipUTs=true` to skip unit tests
* `-DskipITs=true` to skip integration tests
* `-DskipTests=true` to skip all tests

### Running integration tests without rebuild
* `./mvnw exec:exec@create-pod exec:exec@start-jfr-datasource
exec:exec@start-grafana-dashboard exec:exec@start-container
exec:exec@wait-for-container failsafe:integration-test
exec:exec@stop-jfr-datasource exec:exec@stop-grafana exec:exec@stop-container
exec:exec@destroy-pod`
* or `bash repeated-integration-tests.bash 1`.
* To run selected integration tests without rebuilding, append the name(s) of your itest class(es) as an argument to `repeated-integration-tests.bash`, e.g. `bash repeated-integration-tests.bash 1 AutoRulesIT,RecordingWorkflowIT`. Note that modifying a test file does not require a rebuild.

## RUN

### Run on Kubernetes/Openshift
* See the [cryostat-operator](https://github.com/cryostatio/cryostat-operator)

### Run on local podman*
* `run.sh`

Note: If you get a 'No plugin found' error, it is because maven has not downloaded all the necessary plugins. To resolve this error, manually run `./mvnw help:evaluate` to prompt maven to download the missing help plugin.

### Run on local podman with Grafana, jfr-datasource and demo application*
* `smoketest.sh`

To run on local podman, [cgroups v2](https://www.kernel.org/doc/html/latest/admin-guide/cgroup-v2.html) should be enabled.
This allows resource configuration for any rootless containers running on podman. To ensure podman works with cgroups v2, follow these [instructions](https://podman.io/blogs/2019/10/29/podman-crun-f31.html).

Note: If your podman runtime is set to runc v1.0.0-rc91 or later it is not necessary to change it to crun as recommended in the instructions, since this version of runc supports cgroups v2. The article refers to an older version of runc.

## CONFIGURATION

Cryostat can be configured via the following environment variables:

#### Configuration for cryostat

* `CRYOSTAT_WEB_HOST`: the hostname used by the cryostat web server. Defaults to reverse-DNS resolving the host machine's hostname.
* `CRYOSTAT_WEB_PORT`: the internal port used by the cryostat web server. Defaults to 8181.
* `CRYOSTAT_EXT_WEB_PORT`: the external port used by the cryostat web server. Defaults to be equal to `CRYOSTAT_WEB_PORT`.
* `CRYOSTAT_CORS_ORIGIN`: the origin for CORS to load a different cryostat-web instance. Defaults to the empty string, which disables CORS.
* `CRYOSTAT_MAX_WS_CONNECTIONS`: the maximum number of websocket client connections allowed (minimum 1, maximum `Integer.MAX_VALUE`, default `Integer.MAX_VALUE`)
* `CRYOSTAT_AUTH_MANAGER`: the authentication/authorization manager used for validating user accesses. See the `USER AUTHENTICATION / AUTHORIZATION` section for more details. Set to the fully-qualified class name of the auth manager implementation to use, ex. `io.cryostat.net.BasicAuthManager`. Defaults to an AuthManager corresponding to the selected deployment platform, whether explicit or automatic (see below).
* `CRYOSTAT_PLATFORM`: the platform clients used for performing platform-specific actions, such as listing available target JVMs. If `CRYOSTAT_AUTH_MANAGER` is not specified then a default auth manager will also be selected corresponding to the highest priority platform, whether those platforms are specified by the user or automatically detected. Set to the fully-qualified names of the platform detection strategy implementations to use, ex. `io.cryostat.platform.internal.KubeApiPlatformStrategy,io.cryostat.platform.internal.PodmanPlatformStrategy`.
* `CRYOSTAT_ENABLE_JDP_BROADCAST`: enable the Cryostat JVM to broadcast itself via JDP (Java Discovery Protocol). Defaults to `true`.
* `CRYOSTAT_JDP_ADDRESS`: the JDP multicast address to send discovery packets. Defaults to `224.0.23.178`.
* `CRYOSTAT_JDP_PORT`: the JDP multicast port to send discovery packets. Defaults to `7095`.
* `CRYOSTAT_CONFIG_PATH`: the filesystem path for the configuration directory. Defaults to `/opt/cryostat.d/conf.d`.
* `CRYOSTAT_DISABLE_BUILTIN_DISCOVERY`: set to `true` to disable built-in target discovery mechanisms (see `CRYOSTAT_PLATFORM`). Custom Target "discovery" remains available, but discovery via JDP, Kubernetes API, or Podman API is disabled and ignored. This will still allow platform detection to automatically select an `AuthManager`. This is intended for use when Cryostat Discovery Plugins are the only desired mechanism for locating target applications. See #936 and [cryostat-agent](https://github.com/cryostatio/cryostat-agent). Defaults to `false`.
* `CRYOSTAT_K8S_NAMESPACES`: set to a comma-separated list of Namespaces that Cryostat should query to discover target JVM applications with its built-in discovey mechanism.

#### Configuration for Automated Analysis Reports

* `CRYOSTAT_REPORT_GENERATION_MAX_HEAP`: the maximum heap size used by the container subprocess which forks to perform automated rules analysis report generation. The default is `200`, representing a `200MiB` maximum heap size. Too small of a heap size will lead to report generation failing due to Out-Of-Memory errors. Too large of a heap size may lead to the subprocess being forcibly killed and the parent process failing to detect the reason for the failure, leading to inaccurate failure error messages and API responses.

#### Configuration for JMX Connections and Cache

* `CRYOSTAT_JMX_CONNECTION_TIMEOUT_SECONDS`: the maximum wait time for a JMX
  connection to open and a single operation to complete. This is only used for
  specific internally-fired operations that are expected to execute very quickly
  after the connection opens. Default `3`, minimum `1`.
* `CRYOSTAT_TARGET_MAX_CONCURRENT_CONNECTIONS`: the maximum number of concurrent
  JMX connections open. When this number of connections are open any requests
  requiring further connections will block until a previous connection closes.
  Defaults to `-1` which indicates an unlimited number of connections.
* `CRYOSTAT_TARGET_CACHE_TTL`: the time to live (in seconds) for cached JMX
connections. Defaults to `10`, minimum `1`. Any values less than `1` will be
overridden with `1`.

#### Configuration for Logging

* `CRYOSTAT_JUL_CONFIG` : the `java.util.logging.config.file` configuration file for logging via SLF4J Some of Cryostat's dependencies also use java.util.logging for their logging. Cryostat disables [some of these](https://github.com/cryostatio/cryostat-core/tree/main/src/main/resources/config/logging.properties) by default, because they generate unnecessary logs. However, they can be reenabled by overriding the default configuration file and setting the disabled loggers to the desired level.

#### Configuration for Event Templates

* `CRYOSTAT_TEMPLATE_PATH`: the storage path for Cryostat event templates

#### Configuration for Archiving

* `CRYOSTAT_ARCHIVE_PATH`: the storage path for archived recordings
* `CRYOSTAT_PUSH_MAX_FILES`: the maximum number of archived recordings stored in a FIFO manner per target JVM when pushing JFR files using the RecordingsFromIdPostHandler. Mainly used with the [cryostat-agent](https://github.com/cryostatio/cryostat-agent) as a global default configuration for the maximum number of archived JFR recordings to keep on disk per-agent-attached-target, which can be overridden by the agent itself. Defaults to `Integer.MAX_VALUE`, minimum `1`. Any values less than `1` will be overridden with `1`.

#### Configuration for database

* `CRYOSTAT_JDBC_DRIVER`: driver to use for communicating with the database. Defaults to `org.h2.Driver`. `org.postgresql.Driver` is also supported.
* `CRYOSTAT_JDBC_URL`: URL for connecting to the database. Defaults to `jdbc:h2:mem:cryostat;INIT=create domain if not exists jsonb as other` for an h2 in-memory database. Also supported: `jdbc:h2:file:/opt/cryostat.d/conf.d/h2;INIT=create domain if not exists jsonb as other`, or a PostgreSQL URL such as `jdbc:postgresql://cryostat:5432/cryostat`.
* `CRYOSTAT_JDBC_USERNAME`: username for JDBC connection.
* `CRYOSTAT_JDBC_PASSWORD`: password for JDBC connection.
* `CRYOSTAT_JMX_CREDENTIALS_DB_PASSWORD`: encryption password for stored JMX
  credentials.
* `CRYOSTAT_HIBERNATE_DIALECT`: Defaults to `org.hibernate.dialect.H2Dialect`. Also supported: `org.hibernate.dialect.PostgreSQL95Dialect`.
* `CRYOSTAT_HBM2DDL`: Control Hibernate schema DDL. Defaults to `create`.
* `CRYOSTAT_LOG_DB_QUERIES`: Enable verbose logging of database queries. Defaults to `false`.

## MONITORING APPLICATIONS
In order for `cryostat` to be able to monitor JVM application targets the
targets must have RJMX enabled or have the Cryostat Agent installed and
configured. `cryostat` has several strategies for automatic discovery of
potential targets.

The first target discovery mechanism uses the OpenShift/Kubernetes API to list
service endpoints and expose all discovered services as potential targets. This
is runtime dynamic, allowing `cryostat` to discover new services which come
online after `cryostat`, or to detect when known services disappear later.
This requires the `cryostat` pod to have authorization to list services
within its own namespace.

The second discovery mechanism is JDP (Java Discovery Protocol). This relies on
target JVMs being configured with the JVM flags to enable JDP and requires the
targets to be reachable and in the same subnet as `cryostat`. JDP can be enabled
by passing the flag `"-Dcom.sun.management.jmxremote.autodiscovery=true"` when
starting target JVMs; for more configuration options, see
[this document](https://docs.oracle.com/javase/10/management/java-discovery-protocol.htm)
. Once the targets are properly configured, `cryostat` will automatically
discover their JMX Service URLs, which includes the RJMX port number for that
specific target.

The third discovery mechanism is the Podman API. If the Podman API socket is
available at its default filesystem location then Cryostat will query the
`libpod/containers` endpoint to determine what target applications may be
available. Containers must have the Podman label `io.cryostat.connectUrl`
applied, and the value should be the remote JMX or Cryostat Agent HTTP
connection URL that Cryostat can use to communicate with the target.

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

### JMX Connectors

Cryostat supports end-user target applications using other JMX connectors than
RMI (for example, WildFly `remote+http`) using "client library" configuration.
The path pointed to by the environment variable `CRYOSTAT_CLIENTLIB_PATH` is
appended to Cryostat's classpath. This path should be a directory within a
volume mounted to the Cryostat container and containing library JARs (ex.
`jboss-client.jar`) in a flat structure.

In the particular case of WildFly `remote+http`, you might do something like
the following to add this capability:

```bash
$ podman cp wildfly:/opt/jboss/wildfly/bin/client/jboss-client.jar clientlib/
```

## EVENT TEMPLATES

JDK Flight Recorder has event templates, which are preset definition of a set of
events, and for each a set of options and option values. A given JVM is likely
to have some built-in templates ready for use out-of-the-box, but Cryostat
also hosts its own small catalog of templates within its own storage. This
catalog is stored at the path specified by the environment variable
`CRYOSTAT_TEMPLATE_PATH`. Templates can be uploaded to Cryostat and
then used to create recordings.

## ARCHIVING RECORDINGS

`cryostat` supports a concept of "archiving" recordings. This simply means
taking the contents of a recording at a point in time and saving these contents
to a file to the `cryostat` process (as opposed to "active"
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
NoopAuthManager) causes Cryostat to expect a token or credentials via an
`Authorization` header on all potentially sensitive requests, ex. recording
creations and downloads, report generations.

The OpenShiftPlatformClient.OpenShiftAuthManager uses token authentication.
These tokens are passed through to the OpenShift API for authz and this result
determines whether Cryostat accepts the request.

The BasicAuthManager uses basic credential authentication configured with a
standard Java properties file at
`$CRYOSTAT_CONFIG_PATH/cryostat-users.properties`.  The credentials stored in
the Java properties file are the user name and a SHA-256 sum hex of the user's
password. The property file contents should look like:
```
user1=abc123
user2=def987
```
Where `abc123` and `def987` are substituted for the SHA-256 sum hexes of the
desired user passwords. These can be obtained by ex.
`echo -n PASS | sha256sum | cut -d' ' -f1`.

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

## API

Cryostat exposes an HTTP API that provides the backing for its web interface,
but is also intended as an automation or extension point for external clients.
For details about this API see [HTTP_API.md](./docs/HTTP_API.md),
[GRAPHQL.md](./docs/GRAPHQL.md), and
[DISCOVERY_PLUGINS.md](./docs/DISCOVERY_PLUGINS.md).
