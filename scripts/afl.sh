#!/bin/bash
set -e
OLD=$PWD

prepare () {
    OUT=afl
    mkdir -p $OUT
    # cd $OUT
    # echo "Copying $1";
    python3 ~/projects/experiments/rers-2019-states/preprocess.py "RERS/Problem$1/Problem$1.c" > "$OUT/Problem$1.c"
    # cp "RERS/Problem$1/Problem$1.c" $OUT
    # echo "Modifying $1";
    # sed -i 's/extern void __VERIFIER_error(int);/void __VERIFIER_error(int i) {fprintf(stderr, "error_%d ", i);assert(0);}/' "$OUT/Problem$1.c"
    # sed -i 's/scanf("%d", &input);/int ret = scanf("%d", \&input ); if (ret != 1) return 0;/' "$OUT/Problem$1.c"
    mkdir -p $OUT/$1/tests $OUT/$1/new-findings
    # sed -n "s/^.*inputs\[\] = {\s*\(\S*\)}.*$/\1/p" "$OUT/Problem$1.c" | xargs -n 1 -d , | xargs -I % sh -c "echo % > $OUT/$1/tests/%.txt && echo >> $OUT/$1/tests/%.txt"
    echo "Compiling $1";
    afl-clang-fast "$OUT/Problem$1.c" -o "$OUT/Problem$1"
    echo "Fuzzing $1";
    AFL_I_DONT_CARE_ABOUT_MISSING_CRASHES=1 AFL_SKIP_CPUFREQ=1 afl-fuzz -i "$OUT/tests" -o "$OUT/$1/new-findings" "$OUT/Problem$1"
    # ../AFL/afl-2.52b/afl-plot $OUT/$1/findings/ $OUT/$1/plot

    cd $OLD
    ./scripts/analyze_afl.py "$OUT/$1/new-findings/default" "$OUT/Problem$1"
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
