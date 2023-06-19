#!/bin/bash
# shellcheck disable=SC1091

set -e

source "$(dirname "$0")/.env"
export COMPOSE_FILE

# handle compose engine
if [[ -z "${CONTAINER_ENGINE}" ]]; then
    CONTAINER_ENGINE="podman"
fi

if [[ "${CONTAINER_ENGINE}" == "podman" ]]; then
    COMPOSE_ENGINE="podman-compose"
elif [[ "${CONTAINER_ENGINE}" == "docker" ]]; then
    COMPOSE_ENGINE="docker compose"
else
    echo "ERROR: Invalid container engine specified."
    exit 1
fi

if $COMPOSE_ENGINE stop cryostat; then
    sleep 2 # wait for main script to detect container stopped
    $CONTAINER_ENGINE rm cryostat
    $COMPOSE_ENGINE up -d cryostat
    echo "Cryostat restarted!"
else
    echo "Cryostat failed to stop!"
fi
