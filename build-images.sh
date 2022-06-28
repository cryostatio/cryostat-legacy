#!/bin/sh

set -x
set -e

sh "$(dirname $0)/baseImage/build.sh"

mvn -Dheadless=true clean verify

mvn verify
