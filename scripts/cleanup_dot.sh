#!/bin/bash
sed -e "/s\([0-9]\+\) -> s\1 .*\(error\|invalid\)/d" -i "$1"
dot -Tpdf -Grankdir="LR" "$1" -O
