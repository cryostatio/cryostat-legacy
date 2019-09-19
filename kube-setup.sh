#!/bin/sh

set -x
set -e

if ! [ -x "$(command -v jq)" ]; then
    echo 'Error: jq is not installed.' >&2
    exit 1
fi

oc new-project container-jfr

oc create sa discovery

oc create role service-lister --verb=list --resource=services

oc policy add-role-to-user --role-namespace=container-jfr service-lister -z discovery

oc new-app quay.io/rh-jmc-team/container-jfr:latest --name=container-jfr

oc patch dc/container-jfr -p '{"spec":{"template":{"spec":{"serviceAccountName":"discovery"}}}}'

oc expose dc/container-jfr --name=command-channel --port=9090

oc expose svc/command-channel

oc expose svc/container-jfr

oc set env dc/container-jfr CONTAINER_JFR_WEB_PORT=8181

oc set env dc/container-jfr CONTAINER_JFR_EXT_WEB_PORT=80

oc set env dc/container-jfr CONTAINER_JFR_LISTEN_PORT=9090

oc set env dc/container-jfr CONTAINER_JFR_EXT_LISTEN_PORT=80

oc set env dc/container-jfr CONTAINER_JFR_WEB_HOST="$(oc get route/container-jfr -o json | jq -r .spec.host)"

oc set env dc/container-jfr CONTAINER_JFR_LISTEN_HOST="$(oc get route/command-channel -o json | jq -r .spec.host)"

echo "Service will be available at: http://$(oc get route/container-jfr -o json | jq -r .spec.host)"
