#!/bin/bash
set -e
modified=$(stat -c%Z src/main/java/nl/tudelft/instrumentation/symbolic/SymbolicExecutionLab.java)
touch scripts/lastmodified
lastmodified=$(cat scripts/lastmodified)
if [ "$modified" -gt "$lastmodified" ]; then
  mvn clean package
  clear
fi
echo $modified > scripts/lastmodified
java -XX:+UseConcMarkSweepGC -XX:-UseGCOverheadLimit -Xmx4G -cp target/aistr.jar:lib/com.microsoft.z3.jar:./instrumented:. Problem$1
