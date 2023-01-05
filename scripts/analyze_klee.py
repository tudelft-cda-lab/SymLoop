#!/usr/bin/env python3
from sys import argv, float_info
import time
from os import listdir, environ
from collections import defaultdict
from os.path import isfile, join
import re
from tqdm import tqdm
import os


import subprocess
directory = argv[1]
program = argv[2]
starttime_file = argv[3]

regex = re.compile(r'error_([0-9]+)')

def get_output(file, starttime: float):
    modified = float(os.path.getmtime(file))
    my_env = os.environ.copy()
    my_env['KTEST_FILE'] = file
    p = subprocess.Popen(program, env=my_env, stderr=subprocess.PIPE)
    ret = p.wait()
    if ret != 0 and p.stderr is not None:
        output = p.stderr.read().decode()
        assert modified >= 0
        m = regex.match(output)
        if m:
            error = int(m.groups()[0])
            return (error, modified - starttime)
    return None

starttime = float(os.path.getmtime(starttime_file))
files_and_folders = [join(directory, name) for name in listdir(directory)]
files = [f for f in files_and_folders if isfile(f)]
# ktest_files = [f for f in files if f.endswith('.err')]

# ktest_files = [f for f in files if f.endswith('.ktest')]

ktest_files = [f.replace('assert.err', 'ktest') for f in files if f.endswith('.err')]
# print(ktest_files)

from joblib import Parallel, delayed
n_jobs = 100
print('starttime', time.ctime(starttime), starttime)
outputs = Parallel(n_jobs=n_jobs, prefer="processes")(
    delayed(get_output)(f, starttime) for f in tqdm(ktest_files)
)

errors: dict[int, float] = defaultdict(lambda: float_info.max)
for output in outputs:
    if output is not None:
        error, modified = output
        if modified <= errors[error]:
            errors[error] = modified


for error, modified in sorted(errors.items(), key=lambda x: x[0]):
    print(f'Error {error:-3}: {modified}')

print(f'total errors: {len(errors)}')
