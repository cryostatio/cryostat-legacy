# Container-JMC

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

The `run.sh` script can be used to spin up a Docker container of the JMXClient,
running alone but set up so that it is able to introspect itself with JFR. This
can be achieved by doing `sh run.sh` and then typing `connect jmx-client:9091`
into the client shell that appears. Some client shell demo scripts are also
available in the `demos` directory. These can be used with ex.
`sh run.sh "$(more demos/print_help)"`. There are two environment variables
that the client checks during its runtime: `CONTAINER_DOWNLOAD_HOST` and
`CONTAINER_DOWNLOAD_PORT`. These are used by the embedded webserver for
controlling the port and hostname used and displayed when making recordings
available for export (download). These may be set by setting the environment
variable before invoking the `run.sh` shell script, or if this script is not
used, by using the `-e` environment variable flag in the `docker` or `podman`
command invocation.
