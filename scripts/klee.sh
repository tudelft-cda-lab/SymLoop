#!/bin/bash
set -e
KLEE_BASE="$HOME/projects/klee"
KLEE_BIN="$KLEE_BASE/build/bin"
KLEE_INCLUDE="$KLEE_BASE/include/"
KLEE_LIBRARY="$KLEE_BASE/build/lib/"
CLANG_LOC="$HOME/klee_deps/llvm-110-install_O_D_A/bin"
OPTIMIZATION="-O0"
RUN_ID=$(date -Iseconds)
NAME="klee-$2"

if [[ -z $1 ]]; then
    echo "ERROR: usage ./klee.sh PROBLEM NAME"
    echo "please provide which problem to run on or 'all'"
    exit
fi
if [[ -z $NAME ]]; then
    echo "please provide second argument to indicate the name of this experiment"
    exit
fi

prepare () {
    OLD=$PWD
    OUT=experiments/$RUN_ID-$NAME/problem$1
    mkdir -p $OUT
    echo "Copying $1";

    PROBLEM_FILE="RERS/Problem$1/Problem$1.c"
    if test -f "$PROBLEM_FILE"; then
        echo "$PROBLEM_FILE exists."
    else
        PROBLEM_FILE="custom_problems/Problem$1.c"
    fi
    cp $PROBLEM_FILE $OUT/
    echo "Modifying $1";
    sed -i 's/^.*extern void __VERIFIER_error(int);/#include <klee\/klee.h>\
        void __VERIFIER_error(int i) { fprintf(stderr, "error_%d\\n", i); fflush(stderr); assert(0); }/' "$OUT/Problem$1.c"
    # Remove any output
    sed -i '/printf("/d' "$OUT/Problem$1.c"
    sed -i -z 's/while(1)\n.*if\(([^\n]*)\)[^}]*}/while(1) {int input = 0; klee_make_symbolic(\&input, sizeof(input), "input"); klee_assume(!(\1)); if(\1){return 0;}calculate_output(input);}/' "$OUT/Problem$1.c"
    sed -i '/klee_assume/s/\&\&/\&/' "$OUT/Problem$1.c"

    sed -i 's/^.*fprintf(stderr, "Invalid input:.*/exit(0);/' "$OUT/Problem$1.c"
    export LD_LIBRARY_PATH=$KLEE_LIBRARY:$LD_LIBRARY_PATH
    cd $OUT
    $CLANG_LOC/clang $OPTIMIZATION -I $KLEE_INCLUDE -emit-llvm -g -c  "./Problem$1.c" -o "./Problem$1.bc"

    $CLANG_LOC/clang $OPTIMIZATION -I "$KLEE_INCLUDE" -L "$KLEE_LIBRARY" "Problem$1.c" -o "Problem$1-test.bc" -lkleeRuntest

    rm -f start.txt && touch start.txt
    DEFAULT_ARGS="--only-output-states-covering-new -posix-runtime --emit-all-errors -libc=uclibc"
    ADDITIONAL_ARGS="--max-time=24h"
    # $KLEE_BIN/klee $DEFAULT_ARGS --use-merge --optimize -max-time=10min "Problem$1.bc"
    $KLEE_BIN/klee $DEFAULT_ARGS $ADDITIONAL_ARGS "Problem$1.bc"

    echo "SUMMARY"
    $KLEE_BIN/klee-stats ../

    cd $OLD
    python3 ./scripts/analyze_klee.py "$OUT/klee-last" "$OUT/./Problem$1-test.bc" "$OUT/start.txt" | tee "$OUT/errors.txt"

    echo "results in: $OUT/errors.txt"

    cd $OUT/klee-last
    find . -name "*.ktest" -type f -delete
    find . -name "*.kquery" -type f -delete
    find . -name "*.assert.err" -type f -delete
    find . -name "*.early" -type f -delete
    cd $OLD
}

if [ "$1" = "all" ]; then
    for i in "PowersOfTwo" 11 12 13 14 15 17 18; do
        prepare $i
    done;
else
    prepare $1
fi
