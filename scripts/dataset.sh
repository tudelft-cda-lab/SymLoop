mkdir -p RERS
cd RERS
wget https://www.rers-challenge.org/2020/problems/sequential/SeqReachabilityRers2020.zip
wget https://rers-challenge.org/2020/problems/sequential/SeqLtlRers2020.zip
ls *.zip | xargs -L 1 unzip -o
rm -f *.zip
