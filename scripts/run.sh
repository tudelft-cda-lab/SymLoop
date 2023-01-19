#!/bin/bash
set -e
ln -sf "$PWD/rers2020_test_cases/Problem$1Testcases.txt" src/main/resources/tests.txt
modified=$(find src/ | grep src/main/java | xargs stat -c "%Y" | sort -n | tail -n 1)
echo "modified: $modified"
lastmodified=$(cat scripts/lastmodified || echo "0")
echo "lastmodified: $lastmodified"
echo "modified:" $modified
if [ "$modified" -gt "$lastmodified" ]; then
   # mvn -q clean package -
   mvn -q clean package -DskipTests
  # clear
fi
echo $modified > scripts/lastmodified

# java -ea -XX:-UseGCOverheadLimit -Xmx4G -cp target/aistr.jar:lib/com.microsoft.z3.jar:./instrumented:. Problem$1 ${@:2}
java -ea -agentpath:$HOME/projects/async-profiler/build/libasyncProfiler.so=start,event=cpu,file=profile.html,title="Problem$1 $(date '+%T')",minwidth=0.2 -XX:-UseGCOverheadLimit -Xmx4G -cp target/aistr.jar:lib/com.microsoft.z3.jar:./instrumented:. Problem$1 ${@:2}
