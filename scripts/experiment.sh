#!/bin/bash
RUN_ID=$(date -Iseconds)
set -e
mkdir -p experiments/$D
NAME="$2"

if [[ -z $1 ]]; then
    echo "ERROR: usage ./experiment.sh PROBLEM NAME ARGS"
    echo "please provide which problem to run on or 'all'"
    exit
fi
if [[ -z $NAME ]]; then
    echo "please provide second argument to indicate the name of this experiment"
    exit
fi

ARGS="${@:3}"
ARGNAME="${ARGS// /}"
DIR="experiments/$RUN_ID-loop-sym$ARGNAME-$NAME"
mkdir -p $DIR
cp ./target/aistr.jar $DIR
cp ./Errors.class $DIR

run () {
  OLD=$PWD
  OUT=$DIR/problem$1
  mkdir -p $OUT
  cd $OUT
  ARGS="$ARGS"
  echo $ARGS > args.txt
  # for LIMIT in {1..200};
  # do
  #     LIMIT=$LIMIT java -ea -XX:-UseGCOverheadLimit -Xmx4G -cp ../:../aistr.jar:$OLD/lib/com.microsoft.z3.jar:$OLD/instrumented:. Problem$1 $ARGS | tee -a out.txt
  # done
  java -ea -agentpath:$HOME/projects/async-profiler/build/libasyncProfiler.so=start,event=cpu,file=profile.html,title="Problem$1 $(date '+%T')",minwidth=0.2 -XX:-UseGCOverheadLimit -Xmx4G -cp ../:../aistr.jar:$OLD/lib/com.microsoft.z3.jar:$OLD/instrumented:. Problem$1 $ARGS | tee out.txt
  cd $OLD
}

if [ "$1" = "all" ]; then
    for i in "PowersOfTwo" 11 12 13 14 15 17 18; do
    # for i in 11 12 13 14 15 17 18; do
    # for i in 11 12 13 14; do
    # for i in 15 17 18; do
        run $i
    done;
else
    run $1
fi
