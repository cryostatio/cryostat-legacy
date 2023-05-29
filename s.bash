#!/bin/bash

count=$1

merged_yaml=""

for ((i=1; i<=count; i++))
do
  current_yaml="$(sed "s/vertx-jmx/vertx-jmx-${i}/g" compose-vertx-jmx.yaml)"
  
  # Remove "services:" from all but the first iteration
  if [[ $i -gt 1 ]]; then
    current_yaml="${current_yaml//services:/}"
  fi
  
  merged_yaml+="$current_yaml"
done

echo "$merged_yaml" | docker compose -f - up -d

# for example, `$ bash s.bash 10` will create 10 vertx-jmx instances with the correct podman labels attached
