#!/usr/bin/env python3
import matplotlib
from matplotlib import pyplot as plt

import sys
from collections import defaultdict
import re
import os

plt.ion()
from tabulate import tabulate

import numpy as np
import pandas as pd

def read(filename):
    with open(filename) as file:
        return file.read()


def get_params(filename):
    lines = read(filename)
    settings = re.findall(r'Settings: (.*)', lines)
    if len(settings) == 1:
        params = re.findall(r'\w+=[a-zA-Z0-9]+', settings[0])
        params = [p.split('=') for p in params]
        return dict(params)
    else:
        assert len(settings) == 0
        return None
def parse(filename):
    lines = read(filename)
    for err, seconds in re.findall(r'Error\s+(\d+):\s+(\S*)', lines):
        yield (int(err), float(seconds))
    # print(lines)

problemOutput = dict[str, dict[int, float]]


def print_header(header):
    l = len(header)
    target = 18
    if l < target:
        diff = (target - l) // 2
        pad = ''.join([' '] * diff)
        header = pad + header + pad
    print(f'  --===#### {header} ####===--')

def short_name(program):
    split = [p for p in program.split('-') if p.isalpha() or p.startswith('v')]
    return '-'.join(split)
def write_results_to_latex(problem, output: problemOutput, params):
    data = output
    errors = set()
    for program in sorted(data.keys()):
        errors.update(data[program].keys())

    all_params = ['d', 'l', 'm', 'st']

    errors = sorted(list(errors))
    rows = [['Program', *all_params, *errors]]
    for program in sorted(data.keys()):
        row = [short_name(program)]
        for p in all_params:
            if p in params[program]:
                row.append(params[program][p])
            else:
                row.append('')
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

    rows.sort()
    f = open(f'/home/bram/projects/thesis/chapters/results/{problem}.tex', 'w')
    f.write(tabulate(rows, tablefmt='latex', headers='firstrow'))
    f.close()

def get_output_file_names(folder, filenames=['errors.txt', 'out.txt']):
    if os.path.exists(folder):
        klee = os.path.join(folder, 'klee')
        dirname = folder
        if os.path.exists(klee) and os.path.isdir(klee):
            dirname = klee
        for problem in os.listdir(dirname):
            if 'problem' not in problem:
                continue
            problemdir = os.path.join(dirname, problem)
            for f in filenames:
                e = os.path.join(problemdir, f)
                if os.path.exists(e):
                    yield (problem, e)
                    break
            else:
                print(f"no output for problem '{problem}' in '{folder}'")

def get_output_profile_names(folder):
    return list(get_output_file_names(folder, filenames=['solvertimes.csv']))

def generate_bar_chart(problem: str, output: problemOutput):
    errors = set()
    for e in output.values():
        errors.update(e.keys())
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


def aggregate_times(df, group_by='TYPE'):
    return df.groupby(group_by).agg(
        sum=('S', np.sum),
        mean=('S', np.mean),
        count=('S', len))

if __name__ == '__main__':
    folders = sys.argv[1:]
    # program / problem / error -> time
    outputs:dict[str, dict[str, dict[int, float]]] = defaultdict(dict)
    # problem / program / error -> time
    per_problem:dict[str, dict[str, dict[int, float]]] = defaultdict(dict)
    params_per_solution:dict[str, dict[str, str]] = defaultdict(dict)
    rows = []
    for folder in folders:
        t = '+01:00'
        name = folder[folder.index(t)+len(t)+1:]
        fname = folder
        files = list(get_output_file_names(folder))
        folder = name
        for problem, f in files:
            timings = dict(parse(f))
            params = get_params(f)
            if params is not None:
                params_per_solution[folder] = params
            outputs[folder][problem] = timings
            per_problem[problem][folder] = timings
            for e, t in timings.items():
                rows.append((folder, problem, e, t))
        continue
        overall = pd.DataFrame()
        for problem, f in get_output_profile_names(fname):
            df = pd.DataFrame(pd.read_csv(f, delimiter='\t'))
            print(df.columns)
            if 'TYPE' not in df.columns:
                continue
            ns = df['NS']
            assert ns is not None
            df['S'] = ns / 1000000000
            overall = pd.concat([overall, df])
            print_header(problem)
            print(aggregate_times(df))
            # print(df)
            # print(df.groupby('TYPE')['S'].agg(np.sum))
            print()
            # for a, b in df.groupby('LOOPS'):
            #     data = b['MS']
            #     assert data is not None
            #     print(problem, a, f'{np.average(data.to_numpy()):10.0f}')
            # print()
        if 'TYPE' not in overall.columns:
            continue
        print_header('all problems')
        print(aggregate_times(overall))
        print()
        print(aggregate_times(overall, group_by='LOOPS'))
        # print()


    for problem, data in sorted(per_problem.items()):
        write_results_to_latex(problem, data, params_per_solution)
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
