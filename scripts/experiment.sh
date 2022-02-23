#!/bin/bash
D=$(date "+%Y.%m.%d-%H.%M.%S")
set -e
mkdir -p experiments/$D
for i in {11..19}; do
  echo -n "$i, "
  # ./scripts/run.sh $i | grep -A1 stable
  echo "START $i" >> experiments/$D/full.log
  ./scripts/run.sh $i | tee -a experiments/$D/full.log | tee -a experiments/$D/$i.log | tail -n2 | tee -a experiments/$D/summary.log
  echo "DONE $i" >> experiments/$D/full.log
  # echo Done problem $i
  echo "Iter,Visited,Discovered,Errors,Score,TracesPerIter,Tracelength,Time" > experiments/$D/$i.sheet.csv
  cat experiments/$D/$i.log | grep 'Iter' | grep -o '[0-9]\+\(\.[0-9]\+\)\?' | xargs -n 8 | tr ' ' ',' > experiments/$D/$i.sheet.csv
done
