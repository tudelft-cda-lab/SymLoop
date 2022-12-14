#!/bin/bash
# set -e
# cd /home/str/JavaInstrumentation
OUT=klee
mkdir -p $OUT
KLEE_BASE="$HOME/projects/klee"
KLEE_BIN="$KLEE_BASE/build/bin"
KLEE_INCLUDE="$KLEE_BASE/include/"
KLEE_LIBRARY="$KLEE_BASE/build/lib/"
CLANG_LOC="$HOME/klee_deps/llvm-110-install_O_D_A/bin"
OPTIMIZATION="-O1"


prepare () {
    OUT=klee/problem$1
    mkdir -p $OUT
    echo "Copying $1";
    cp "RERS/Problem$1/Problem$1.c" $OUT/
    echo "Modifying $1";
    sed -i 's/^.*extern void __VERIFIER_error(int);/#include <klee\/klee.h>\
        void __VERIFIER_error(int i) { fprintf(stderr, "error_%d", i); fflush(stderr); assert(0); }/' "$OUT/Problem$1.c"
    sed -i '/printf("/d' "$OUT/Problem$1.c"
    sed -i -z 's/while(1)\n.*if\(([^\n]*)\)[^}]*}/int length = 20;int program[length];klee_make_symbolic(program, sizeof(program), "program");for (int i = 0; i < length; ++i) {int input = program[i];if(\1){return 0;}calculate_output(input);}/' "$OUT/Problem$1.c"
    sed -i 's/^.*fprintf(stderr, "Invalid input:.*/exit(0);/' "$OUT/Problem$1.c"
    export LD_LIBRARY_PATH=$KLEE_LIBRARY:$LD_LIBRARY_PATH
    $CLANG_LOC/clang $OPTIMIZATION -I $KLEE_INCLUDE -emit-llvm -g -c  "$OUT/Problem$1.c" -o "$OUT/Problem$1.bc"
    cd $OUT

    $KLEE_BIN/klee --optimize -posix-runtime -libc=uclibc -max-time=1min "Problem$1.bc"

    echo "SUMMARY"
    $KLEE_BIN/klee-stats ../
    $CLANG_LOC/clang $OPTIMIZATION -I "$KLEE_INCLUDE" -L "$KLEE_LIBRARY" "Problem$1.c" -o "Problem$1-test.bc" -lkleeRuntest

    rm -f errors.txt
    pwd
    for f in $(ls -tr ./klee-last/ | grep ktest);
    do
        ERR=$(KTEST_FILE="klee-last/$f" "./Problem$1-test.bc" 2> out.txt 1> /dev/null)
        ERR=$(cat out.txt | grep Assertion)
        if [[ ! -z $ERR ]]; then
            stat --printf="%Y $f " "./klee-last/$f" | tee -a errors.txt
            echo "$ERR" | grep -o -i -E "error_[0-9]+" | tee -a errors.txt
        fi
    done;
    cat errors.txt | sort -s -t" " -u -k3,3 | sort > sorted.txt

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
