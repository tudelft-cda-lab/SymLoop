mkdir -p RERS
cd RERS
wget https://rers-challenge.org/2019/problems/sequential/SeqReachabilityRers2019_Mar_9th.zip
wget https://rers-challenge.org/2019/problems/sequential/SeqLtlRers2019_Mar_9th.zip
ls *.zip | xargs -L 1 unzip
mv Seq*/Problem* .
rm -r $(ls | grep -v Problem)
cd ../
