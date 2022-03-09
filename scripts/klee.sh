#!/bin/bash
# set -e
cd /home/str/JavaInstrumentation
OUT=klee
mkdir -p $OUT


prepare () {
    OUT=klee/problem$1
    mkdir -p $OUT
    echo "Copying $1";
    cp "../RERS/Problem$1/Problem$1.c" $OUT/
    echo "Modifying $1";
    sed -i 's/^.*extern void __VERIFIER_error(int);/#include <klee\/klee.h>\
void __VERIFIER_error(int i) { fprintf(stderr, "error_%d ", i); assert(0); }/' "$OUT/Problem$1.c"
    sed -i -z 's/while(1)\n.*if\(([^\n]*)\)[^}]*}/int length = 20;int program[length];klee_make_symbolic(program, sizeof(program), "program");for (int i = 0; i < length; ++i) {int input = program[i];if(\1){return 0;}calculate_output(input);}/' "$OUT/Problem$1.c"
    clang-6.0 -I "/home/str/klee/include" -emit-llvm -g -c  "$OUT/Problem$1.c" -o "$OUT/Problem$1.bc"
    cd $OUT
    # /home/str/klee/build/bin/klee "Problem$1.bc"
    # /home/str/klee/build/bin/klee -max-time=10min "Problem$1.bc"
    /home/str/klee/build/bin/klee-stats .
    echo "SUMMARY"
    /home/str/klee/build/bin/klee-stats ../
    export LD_LIBRARY_PATH=/home/str/klee/build/lib/:$LD_LIBRARY_PATH
    clang-6.0 -I "/home/str/klee/include" -L "/home/str/klee/build/lib" "Problem$1.c" -o "Problem$1-test.bc" -lkleeRuntest
    rm errors.txt
    for f in $(ls -tr ./klee-last/ | grep ktest | head -n 10000);
    do
        # cat $f | $OUT/Problem$1 2>> $OUT/$1/errors.txt 1> /dev/null
        # /home/str/klee/build/bin/ktest-tool $f
        # echo "$f" >> "errors.txt"
        KTEST_FILE="klee-last/$f" "./Problem$1-test.bc" 2> >(grep Assertion | tee -a errors.txt && stat --printf="%Y $f " "./klee-last/$f" | tee -a errors.txt) 1> /dev/null
    done;
    # mkdir -p $OUT/$1/tests $OUT/$1/findings
    # sed -n "s/^.*inputs\[\] = {\s*\(\S*\)}.*$/\1/p" "$OUT/Problem$1.c" | xargs -n 1 -d , | xargs -I % sh -c "echo % > $OUT/$1/tests/%.txt && echo >> $OUT/$1/tests/%.txt"
    # echo "Compiling $1";
    # ../AFL/afl-2.52b/afl-gcc "$OUT/Problem$1.c" -o "$OUT/Problem$1"
    # echo "Fuzzing $1";
    # AFL_I_DONT_CARE_ABOUT_MISSING_CRASHES=1 AFL_SKIP_CPUFREQ=1 ../AFL/afl-2.52b/afl-fuzz -i "$OUT/$1/tests" -o "$OUT/$1/findings" "$OUT/Problem$1"
    # ../AFL/afl-2.52b/afl-plot $OUT/$1/findings/ $OUT/$1/plot

    # echo > $OUT/$1/errors.txt
    # set +e
    # for f in $(ls -tr $OUT/$1/findings/crashes/id*);
    # do
    #     echo $f >> $OUT/$1/errors.txt
    #     cat $f | $OUT/Problem$1 2>> $OUT/$1/errors.txt 1> /dev/null
    # done;
    # clear
    # cat $OUT/$1/errors.txt | grep error | sort -V | uniq | tee $OUT/$1/final.txt
    # set -e
    # echo Found $(cat $OUT/$1/final.txt | wc -l) distinct errors
}
# for i in {11..19}; do
# prepare $i
# }
prepare $1
