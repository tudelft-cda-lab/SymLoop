#!/bin/bash
D=$(date "+%Y.%m.%d-%H.%M.%S")
set -e
for i in {11..19}; do
  echo -n "$i, "
  # ./scripts/run.sh $i | grep -A1 stable
  echo "START $i" >> experiments/$D.full.log
  ./scripts/run.sh $i | tee -a experiments/$D.full.log | tail -n2 | tee -a experiments/$D.log
  echo "DONE $i" >> experiments/$D.full.log
  # echo Done problem $i
done
