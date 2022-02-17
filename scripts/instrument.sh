#!/bin/bash
if [ -z $1 ]; then
  echo "Please specify an problem number"; 
else
  cd /home/str/JavaInstrumentation
  # mvn clean package
  mkdir -p instrumented
  java -cp target/aistr.jar nl.tudelft.instrumentation.Main --type=fuzzing --file="../RERS/Problem$1/Problem$1.java" > "instrumented/Problem$1.java"
  javac -cp target/aistr.jar:. Errors.java "instrumented/Problem$1.java"
  # java -cp target/aistr.jar:./instrumented:. "Problem$1"
fi
