# Container-JFR

## SEE ALSO
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
- Podman/Docker

## BUILD
The gradle build expects to be able to find Java Mission Control (JMC) 7
artefacts in the local Maven repository. To ensure these are available, clone
the JMC project at the [JMC homepage](https://hg.openjdk.java.net/jmc/jmc7)
and follow its build instructions. Run `mvn install` in the jmc project root to
install its artefacts to the local repository. After this is complete, the
project in this repository may be built locally. This can be done with
`./gradlew build`.

Tests can be run with `./gradlew check`, or for an interactive watch mode,
`./gradlew -it test`.

A Docker image can be built to your local Docker image registry using
`./gradlew jibDockerBuild`. Take note that the standard `./gradlew build`
will not only build the image but will attempt to publish it to Dockerhub.

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

For an overview of the available commands and their functionalities, see
[this document](COMMANDS.md).

## MONITORING APPLICATIONS
In order for `container-jfr` to be able to monitor JVM application targets, the
targets must have RJMX enabled. The default expected listening port is 9091.
Targets listening on other ports are still connectable by `container-jfr` but
will not be automatically discoverable.

To enable RJMX on this port, the following JVM flags should be passed at target
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
