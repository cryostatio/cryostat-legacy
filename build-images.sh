#!/bin/sh

set -x
set -e

mvn -cryostat.minimal=true clean verify

mvn verify
