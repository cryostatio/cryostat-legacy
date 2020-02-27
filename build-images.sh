#!/bin/sh

set -x
set -e

mvn -Dcontainerjfr.minimal=true clean verify

mvn verify
