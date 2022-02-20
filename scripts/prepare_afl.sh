#!/bin/bash
set -e
cd /home/str/JavaInstrumentation
mkdir -p problems

prepare () {
    echo "Copying $1";
    cp "../RERS/Problem$1/Problem$1.c" problems/
    echo "Modifying $1";
    sed -i 's/extern void __VERIFIER_error(int);/void __VERIFIER_error(int i) {fprintf(stderr, "error_%d ", i);assert(0);}/' "problems/Problem$1.c"
    sed -i 's/scanf("%d", &input);/int ret = scanf("%d", \&input ); if (ret != 1) return 0;/' "problems/Problem$1.c"
    mkdir -p problems/$1
    mkdir -p problems/$1/tests
    mkdir -p problems/$1/findings
    sed -n "s/^.*inputs\[\] = {\s*\(\S*\)}.*$/\1/p" "problems/Problem$1.c" | xargs -n 1 -d , | xargs -I % sh -c "echo % > problems/$1/tests/%.txt && echo >> problems/$1/tests/%.txt"
    echo "Compiling $1";
    ../AFL/afl-2.52b/afl-gcc "problems/Problem$1.c" -o "problems/Problem$1"
    echo "Fuzzing $1";
    AFL_I_DONT_CARE_ABOUT_MISSING_CRASHES=1 AFL_SKIP_CPUFREQ=1 ../AFL/afl-2.52b/afl-fuzz -i "problems/$1/tests" -o "problems/$1/findings" "problems/Problem$1"

    echo > problems/$1/errors.txt
    set +e
    for f in $(ls problems/$1/findings/crashes/id*);
    do
        cat $f | problems/Problem$1 2>> problems/$1/errors.txt 1> /dev/null
    done;
    clear
    cat problems/$1/errors.txt | grep error | sort -V | uniq | tee problems/$1/final.txt
    set -e
}
prepare $1
