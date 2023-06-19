# Cryostat compose testing README (DEPRECATED/needs updates)

There are multiple yaml files in the base directory used for testing Cryostat that is compatible with the [Compose Specification](https://github.com/compose-spec/compose-spec/blob/master/spec.md). It is used by the `compose-test.bash` script to test various scenarios. The `test-restart.bash` script is used to test restarting the Cryostat container.

Read the Cryostat CONFIGURATION section of the main [README](./README.md) for more information on Cryostat-specific environment variables.

## Requirements
* [podman-compose 1.0.7+](https://github.com/containers/podman-compose#installation) or [Docker Engine](https://docs.docker.com/engine/install/)
* Bash

## Environment variables for testing

### General

- **COMPOSE_PROJECT_NAME**: The name of the project. Used to specify the containers within the project. Defaults to the `.env` file's `COMPOSE_PROJECT_NAME` variable. If not set, defaults to `cryostat`. More info [here](https://docs.docker.com/compose/environment-variables/envvars/#compose_project_name).

- **CONTAINER_ENGINE**: The container engine to use for the compose file. Defaults to `podman`. Set to `docker` to use `docker compose` instead.

### Testing scenarios
The `CT` prefix stands for *Cryostat Test* or *Compose Test*.

- **CT_EN_RULES**: Use to test automated rules. Creates an enabled automated rule that will trigger on Cryostat startup that matches all targets.

- **CT_EN_SCALING**: Use to test targets that periodically scale, and restart.

- **CT_EN_INVALID**: Use to enable invalid targets. One target has an invalid Agent callback URI, and the other has an invalid `io.cryostat.connectUrl` Podman label.

- **CT_EN_DUPLICATE**: Use to test an environment with two instances of Cryostat. The second instance will be available at `localhost:8282`.

- **CT_EN_POSTGRES**: Use to test a Cryostat instance with a *Postgres* database. The database will be available at `localhost:5432`.

- **CT_JMX_REPLICAS**: Set the number of replicas for the JMX test app. Defaults to 1.

- **CT_AGENT_REPLICAS**: Set the number of replicas for the Agent test app. Defaults to 1.

## Scripts

- **compose-test.bash**: Runs the compose test with the default variables. The following options are available:
    * `-a targets`: Sets the number of agent targets.
    * `-j targets`: Sets the number of JMX targets.
    * `-d`: Enables Cryostat duplicate.
    * `-i`: Enables invalid targets.
    * `-p`: Enables *Postgres* database.
    * `-r`: Enables automated rule that matches all targets.
    * `-s`: Enables periodic target scaling/restarting.
    * `-A`: Enables all of the above; Sets number of external targets to 10 each.

- **test-restart.bash**: Restarts the Cryostat container, but keeps the other containers running. It assumes that the `compose-test.bash` script is currently being executed and that an instance of the Cryostat container is already running."

