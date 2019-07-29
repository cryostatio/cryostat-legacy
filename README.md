# Container-JFR

## SEE ALSO

[Trello](https://trello.com/b/zoQx1GxV/jmc-cloud) for project planning.

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
- JDK11+
- JMC
- Gradle

Run:
- Podman/Docker, OpenShift/Minishift, or other container platform

## BUILD
[container-jfr-core](https://github.com/rh-jmc-team/container-jfr-core) is a
required dependency, which is not currently published in an artefact repository
and so much be built and installed into the Maven local repository.
Instructions for doing so are available at that project's README.

Once the `container-jfr-core` local dependency is made available,
`./gradlew build` will build the project.

Tests can be run with `./gradlew check`, or for an interactive watch mode,
`./gradlew -it test`.

A Docker image can be built to your local Docker image registry using
`./gradlew jibDockerBuild`. Take note that the standard `./gradlew build`
will not only build the image but will attempt to publish it to Dockerhub.

If the environment variable `CONTAINER_JFR_DEBUG` is set to a non-empty
string at build time then a debug image will be built, containing a debug shell
environment within the image and enabling debug printing from the application.

## RUN
For a basic development non-containerized smoketest, use `./gradlew run`, or
`./gradlew run --args="client-args-here"`.

The client can operate in three different command execution modes. These are
batch (scripted), interactive TTY, and interactive socket. To use batch mode,
simply pass a single argument string to the client at runtime, ex.
`./gradlew run --args="'help; ip; hostname'"`. For interactive TTY mode, either
invoke the client with no args (`./gradlew run --args="''"`) or with the flag
`-it`: `./gradlew run --args="-it"`. And for interactive socket mode, pass the
flag `-d`: `./gradlew run --args="-d"`.

The `run.sh` script can be used to spin up a Docker container of the Container
JFR Client, running alone but set up so that it is able to introspect itself
with JFR. This can be achieved by doing `sh run.sh -it` and then typing
`connect container-jfr:9091` into the client shell that appears. When running in
this container, all three execution modes described above are still available
and accessible using the same mthods. Some client shell demo scripts are also
available in the `demos` directory. These can be used with batch mode, ex.
`sh run.sh "$(more demos/print_help)"`.

There are three environment variables that the client checks during its
runtime: `CONTAINER_JFR_DOWNLOAD_HOST`, `CONTAINER_JFR_DOWNLOAD_PORT`, and
`LISTEN_PORT`. The former two are used by the embedded webserver for
controlling the port and hostname used and displayed when making recordings
available for export (download). The latter is used when running the client in
daemon/socket mode and controls the port that the client listens for
connections on. These may be set by setting the environment variable before
invoking the `run.sh` shell script, or if this script is not used, by using the
`-e` environment variable flag in the `docker` or `podman` command invocation.

The application can also be easily set up and configured to run in a more full-
fledged container application platform that supports the Docker/Podman
container image format, such as OpenShift. For an example of such a project
setup, see [jmc-robots-demo](https://github.com/rh-jmc-team/jmc-robots-demo/blob/master/minishift/setup.sh)

For an overview of the available commands and their functionalities, see
[this document](COMMANDS.md).

## MONITORING APPLICATIONS
In order for `container-jfr` to be able to monitor JVM application targets, the
targets must have RJMX enabled. The primary target discovery mechanism is based
on Kubernetes environment variable service discovery. If no such Kubernetes-
style environment variables are detected at runtime then `container-jfr` will
fall back to a port-scanning discovery mechanism. The default expected
listening port is 9091.  Targets listening on other ports are still connectable
by `container-jfr` but will not be automatically discoverable via port-scanning.

To enable RJMX on port 9091, the following JVM flags should be passed at target
startup:

```
    '-Dcom.sun.management.jmxremote.rmi.port=9091',
    '-Dcom.sun.management.jmxremote=true',
    '-Dcom.sun.management.jmxremote.port=9091',
    '-Dcom.sun.management.jmxremote.ssl=false',
    '-Dcom.sun.management.jmxremote.authenticate=false',
    '-Dcom.sun.management.jmxremote.local.only=false',
    '-Djava.rmi.server.hostname=$TARGET_HOSTNAME'
```

The `java.rmi.server.hostname` value should be substituted with the actual
hostname of the machine or container which will be running the target JVM.
For example, in a Podman or Docker deployment scenario, the
`java.rmi.server.hostname` value should correspond to the value passed to the
`--hostname` flag on the `podman run`/`docker run` invocation.

The port number 9091 is arbitrary and may be configured to suit individual
deployments, so long as the two `port` properties above match the desired port
number and the deployment network configuration allows connections on the
configured port. As noted above, the final caveat is that in non-Kube
deployments, port 9091 is expected for automatic port-scanning target discovery.
