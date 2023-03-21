#!/bin/bash
set -e
ln -sf "$PWD/rers2020_test_cases/Problem$1Testcases.txt" src/main/resources/tests.txt
modified=$(find src/ | grep src/main/java | xargs stat -c "%Y" | sort -n | tail -n 1)
lastmodified=$(cat scripts/lastmodified || echo "0")
if [ "$modified" -gt "$lastmodified" ]; then
   # mvn -q clean package -
   # mvn clean -q -T1C --offline package -DskipTests
   mvn compile -q
   echo $modified > scripts/lastmodified
  # clear
else
   echo "SKIPPING BUILD"
fi


# java -ea -XX:-UseGCOverheadLimit -Xmx4G -cp "target/classes:$(cat .runclasspath):lib/com.microsoft.z3.jar:./instrumented:." Problem$1 ${@:2}
# echo "$(cat .classpath):lib/com.microsoft.z3.jar:$PWD/instrumented:." Problem$1 ${@:2}
# java -ea -agentpath:$HOME/projects/async-profiler/build/libasyncProfiler.so=start,event=cpu,file=profile.html,title="Problem$1 $(date '+%T')",minwidth=0.2 -XX:-UseGCOverheadLimit -Xmx4G -cp target/aistr.jar:lib/com.microsoft.z3.jar:./instrumented:. Problem$1 ${@:2}
java -ea -agentpath:$HOME/projects/async-profiler/build/libasyncProfiler.so=start,event=cpu,file=profile.html,title="Problem$1 $(date '+%T')",minwidth=0.2 -XX:-UseGCOverheadLimit -Xmx4G -cp "target/classes:$(cat .runclasspath):lib/com.microsoft.z3.jar:./instrumented:." Problem$1 ${@:2}
