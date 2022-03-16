#!/bin/bash
set -e
cd /home/str/JavaInstrumentation
mkdir -p instrumented

instrument () {
    echo "Instrumenting $1";
    # java -XX:+UseConcMarkSweepGC -XX:-UseGCOverheadLimit -Xmx8096m -cp target/aistr.jar nl.tudelft.instrumentation.Main --type=patching --file="../RERS/Problem$1/Problem$1.java" > "instrumented/Problem$1.java"
    java -XX:+UseConcMarkSweepGC -XX:-UseGCOverheadLimit -Xmx8096m -cp target/aistr.jar nl.tudelft.instrumentation.Main --type=patching --file="../RERS2020Buggy/Problem$1.java" > "instrumented/Problem$1.java"
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
