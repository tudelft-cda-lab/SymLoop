#!/bin/bash
cd /home/str/JavaInstrumentation
mkdir -p instrumented
if [ -z $1 ]; then
  for i in {1..9}; do
    echo "Instrumenting $i";
    java -cp target/aistr.jar nl.tudelft.instrumentation.Main --type=fuzzing --file="../RERS/Problem$i/Problem$i.java" > "instrumented/Problem$i.java"
    javac -cp target/aistr.jar:. Errors.java "instrumented/Problem$i.java"
  done
  for i in {11..19}; do
    echo "Instrumenting $i";
    java -cp target/aistr.jar nl.tudelft.instrumentation.Main --type=fuzzing --file="../RERS/Problem$i/Problem$i.java" > "instrumented/Problem$i.java"
    javac -cp target/aistr.jar:. Errors.java "instrumented/Problem$i.java"
  done
else
  # mvn clean package
  java -cp target/aistr.jar nl.tudelft.instrumentation.Main --type=fuzzing --file="../RERS/Problem$1/Problem$1.java" > "instrumented/Problem$1.java"
  javac -cp target/aistr.jar:. Errors.java "instrumented/Problem$1.java"
  # java -cp target/aistr.jar:./instrumented:. "Problem$1"
fi
