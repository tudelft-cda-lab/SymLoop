#!/bin/bash
RUN_ID=$(date -Iseconds)
set -e
mkdir -p experiments/$D
NAME=$2

if [[ -z $1 ]]; then
    echo "ERROR: usage ./klee.sh PROBLEM NAME"
    echo "please provide which problem to run on or 'all'"
    exit
fi
if [[ -z $NAME ]]; then
    echo "please provide second argument to indicate the name of this experiment"
    exit
fi

DIR=experiments/$RUN_ID-$NAME-java
mkdir -p $DIR
cp ./target/aistr.jar $DIR
cp ./Errors.class $DIR

run () {
  OLD=$PWD
  OUT=$DIR/problem$1
  mkdir -p $OUT
  cd $OUT
  ARGS="--max-time 10m -d 0 -l 0"
  echo $ARGS > args.txt
  java -ea -XX:-UseGCOverheadLimit -Xmx4G -cp ../:../aistr.jar:$OLD/lib/com.microsoft.z3.jar:$OLD/instrumented:. Problem$1 $ARGS | tee out.txt
  cd $OLD
}

if [ "$1" = "all" ]; then
    for i in 11 12 13 14 15 17 18; do
        run $i
    done;
else
    run $1
fi
