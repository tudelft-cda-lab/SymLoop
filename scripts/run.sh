#!/bin/bash
modified=$(stat -c%Z src/main/java/nl/tudelft/instrumentation/fuzzing/FuzzingLab.java)
lastmodified=$(cat scripts/lastmodified)
echo $modified $lastmodified
if [ "$modified" -gt "$lastmodified" ]; then
  echo $modified > scripts/lastmodified
  mvn clean package
  clear
fi
cat scripts/lastmodified
echo $modified > scripts/lastmodified
java -cp target/aistr.jar:./instrumented:. Problem$1
