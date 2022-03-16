#!/bin/bash
set -e
modified=$(stat -c%Z src/main/java/nl/tudelft/instrumentation/patching/PatchingLab.java)
touch scripts/lastmodified
lastmodified=$(cat scripts/lastmodified)
if [ "$modified" -gt "$lastmodified" ]; then
  mvn clean package
  clear
fi
echo $modified > scripts/lastmodified
ln -sf "$PWD/rers2020_test_cases/Problem$1Testcases.txt" src/main/resources/tests.txt
cat src/main/resources/tests.txt
java -XX:+UseConcMarkSweepGC -XX:-UseGCOverheadLimit -Xmx4G -cp target/aistr.jar:lib/com.microsoft.z3.jar:./instrumented:. Problem$1
