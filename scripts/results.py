#!/usr/bin/env python3
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
    for err, seconds in re.findall(r'Error\s+(\d+):\s+(\S*)', lines):
        # print(err, seconds)
        yield (int(err), float(seconds))

problemOutput = dict[str, dict[int, float]]


def write_results_to_latex(problem, output: problemOutput):
    data = output
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
                v = data[program][error]
                if v >= 10:
                    row.append(f'{v:.0f}')
                else:
                    row.append(f'{v:.1f}')
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


def generate_bar_chart(problem: str, output: problemOutput):
    print(problem, output)
    errors = set()
    [errors.update(e.keys()) for e in output.values()]
    print(problem, errors)
    programs = dict()
    errors = sorted(list(errors))
    default = -100
    for program, times in output.items():
        programs[program] = [times[error] if error in times else default for error in errors]
                # ]
            # if error in times:
                # programs[program].append(times[error])
            # else:
                # programs[program].append(-1)
    print(programs)
    x = np.arange(len(errors))
    width = 1.0 / (len(programs) + 4)
    programs = sorted(programs.items())
    for i, (program, d) in enumerate(programs):
        plt.bar(x + i * width, d, width)
    plt.xticks(x + 0.5 * width * len(programs), errors)
    plt.xlabel('Errors')
    plt.ylabel('Time')
    plt.legend([p for p, _ in programs])
    plt.title(f'Time to find errors on {problem}')
    plt.yscale("log") 
    # plt.show(block=True)

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
        write_results_to_latex(problem, data)
        generate_bar_chart(problem, data)



    # df = pd.DataFrame(rows, columns=['Program', 'Problem', 'Error', 'Time'])
    # print(df)
    # p = df[df['Problem'] == 'problem11']
    # p = p.groupby('Program')
    # print(p)
    # a = p.plot(x='Error', kind='bar', stacked=True, y='Time')
    # plt.show(block=True)
    
    exit()
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
