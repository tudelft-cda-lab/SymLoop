import matplotlib
from matplotlib import pyplot as plt

import sys
from collections import defaultdict
import re
import os

# matplotlib.use('GTK3Agg')
# matplotlib.use('Agg')
plt.ion()
print(matplotlib.rcParams['interactive'])
print(matplotlib.matplotlib_fname())
from tabulate import tabulate

import numpy as np
import pandas as pd

def read(filename):
    with open(filename) as file:
        return file.read()

def parse(filename):
    lines = read(filename)
    for err, seconds in re.findall(r'Error\s+(\d+):\s+(.*)', lines):
        # print(err, seconds)
        yield (int(err), float(seconds))


def get_output_file_names(folder):
    if os.path.exists(folder):
        klee = os.path.join(folder, 'klee')
        dirname = folder
        if os.path.exists(klee) and os.path.isdir(klee):
            dirname = klee
        for problem in os.listdir(dirname):
            if 'problem' not in problem:
                continue
            problemdir = os.path.join(dirname, problem)
            e = os.path.join(problemdir, 'errors.txt')
            o = os.path.join(problemdir, 'out.txt')
            if os.path.exists(e):
                yield (problem, e)
            elif os.path.exists(o):
                yield (problem, o)
            else:
                print(f"no output for problem '{problem}' in '{folder}'")



if __name__ == '__main__':
    folders = sys.argv[1:]
    # program / problem / error -> time
    outputs:dict[str, dict[str, dict[int, float]]] = defaultdict(dict)
    # problem / program / error -> time
    per_problem:dict[str, dict[str, dict[int, float]]] = defaultdict(dict)
    rows= []
    for folder in folders:
        t = '+01:00'
        name = folder[folder.index(t)+len(t)+1:]
        files = list(get_output_file_names(folder))
        folder = name
        for problem, f in files:
            timings = dict(parse(f))
            outputs[folder][problem] = timings
            per_problem[problem][folder] = timings
            for e, t in timings.items():
                rows.append((folder, problem, e, t))


    for problem, data in sorted(per_problem.items()):
        errors = set()
        for program in sorted(data.keys()):
            errors.update(data[program].keys())
        print(problem,errors)
        errors = sorted(list(errors))
        rows = [['Program / Error', *errors]]
        for program in sorted(data.keys()):
            row = [program]
            for error in errors:
                if error in data[program]:
                    row.append(f'{data[program][error]:.1f}')
                else:
                    row.append('-')
            rows.append(row)
        print(rows)
        print(tabulate(rows))
        print('Tabulate Table:')
        print(tabulate(rows, headers='firstrow'))
        df = pd.DataFrame(rows)
        f = open(f'/home/bram/projects/thesis/chapters/results/{problem}.tex', 'w')
        f.write(tabulate(rows, tablefmt='latex', headers='firstrow'))
        f.close()

    exit()

    df = pd.DataFrame(rows, columns=['Program', 'Problem', 'Error', 'Time'])
    for problem, group in df.groupby('Problem'):
        # print(problem)
        # print(group)
        errors = group['Error'].to_numpy()
        print(errors)
        # for program, group in group.groupby('Program'):
        #     row = group[['Error', 'Time']].to_numpy()
        #     print('row', row[:,0], row[:,1])
        #     print(plt.scatter(row[:,0], row[:,1], label=program))
        # plt.legend()
        # plt.title(problem)
        # plt.show(block=True)
        # print(matplotlib.rcParams['interactive'])
