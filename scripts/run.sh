#!/bin/bash
set -e
modified=$(stat -c%Z src/main/java/nl/tudelft/instrumentation/fuzzing/FuzzingLab.java)
touch scripts/lastmodified
lastmodified=$(cat scripts/lastmodified)
echo $modified > scripts/lastmodified
if [ "$modified" -gt "$lastmodified" ]; then
  mvn clean package
  clear
fi
echo $modified > scripts/lastmodified
java -cp target/aistr.jar:./instrumented:. Problem$1
