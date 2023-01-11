#!/bin/bash
set -e
ln -sf "$PWD/rers2020_test_cases/Problem$1Testcases.txt" src/main/resources/tests.txt
modified=$(stat -c%Z src/main/java/nl/tudelft/instrumentation/patching/PatchingLab.java)
touch scripts/lastmodified
lastmodified=$(cat scripts/lastmodified)
#if [ "$modified" -gt "$lastmodified" ]; then
  # mvn -q clean package -
  mvn -q clean package -DskipTests
  clear
#fi
echo $modified > scripts/lastmodified

# java -ea -XX:-UseGCOverheadLimit -Xmx4G -cp target/aistr.jar:lib/com.microsoft.z3.jar:./instrumented:. Problem$1 ${@:2}
java -ea -agentpath:$HOME/projects/async-profiler/build/libasyncProfiler.so=start,event=cpu,file=profile.html -XX:-UseGCOverheadLimit -Xmx4G -cp target/aistr.jar:lib/com.microsoft.z3.jar:./instrumented:. Problem$1 ${@:2}
