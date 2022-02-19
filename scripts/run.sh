#!/bin/bash
modified=$(stat -c%Z src/main/java/nl/tudelft/instrumentation/fuzzing/FuzzingLab.java)
lastmodified=$(cat scripts/lastmodified)
if [ "$modified" -gt "$lastmodified" ]; then
  echo $modified > scripts/lastmodified
  mvn clean package
  clear
fi
echo $modified > scripts/lastmodified
java -cp target/aistr.jar:./instrumented:. Problem$1
