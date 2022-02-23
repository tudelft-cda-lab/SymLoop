#!/bin/bash
set -e
for i in {11..19}; do
  echo -n "$i, "
  # ./scripts/run.sh $i | grep -A1 stable
  ./scripts/run.sh $i | tail -n1
  # echo Done problem $i
done
