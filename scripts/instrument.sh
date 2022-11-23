#!/bin/bash
set -e
# cd /home/str/JavaInstrumentation
mkdir -p instrumented
DATASET="./RERS"
CUSTOM_DATASET="./custom_problems"

# If the target is not built yet:
# mvn clean package

instrument () {
    FILE="$DATASET/Problem$1/Problem$1.java"
    if [ -f "$FILE" ]; then
      echo "Normal problem: $1"
    else 
      echo "Custom problem: $1"
      FILE="$CUSTOM_DATASET/Problem$1.java"
    fi
    echo "Instrumenting $1";
    java -XX:+UseG1GC -Xmx22g -cp target/aistr.jar nl.tudelft.instrumentation.Main --type=symbolic --file="$FILE" > "instrumented/Problem$1.java"
    echo "Compiling $1";
    javac -cp target/aistr.jar:lib/com.microsoft.z3.jar:. Errors.java "instrumented/Problem$1.java"
}

if [ -z $1 ]; then
  for i in {1..9}; do
    instrument $i
  done
  for i in {11..19}; do
    instrument $i
  done
else
  # mvn clean package
  instrument $1
fi
