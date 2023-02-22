#!/usr/bin/env python3
from stmlearn.learners import TTTDFALearner, LStarDFALearner

from stmlearn.equivalencecheckers import SmartWmethodEquivalenceChecker, WmethodEquivalenceChecker, BFEquivalenceChecker
from stmlearn.teachers import Teacher
from typing import Iterable

from stmlearn.suls import SUL

import subprocess
import re


class RERS_Problem(SUL):
    def __init__(self, cp: str, problem: str):
        print(problem)
        self.problem_file = problem
        self.cp = cp
        self.sub = None
        self.start()
        self.mem = dict()
        self.current = ''

    def java_name(self):
        return self.cp + '/' + self.problem_file + '.java'

    def start(self):
        if self.sub is not None:
            self.sub.kill()
        self.sub = subprocess.Popen(["java", "-cp", self.cp, self.problem_file], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)

    def get_alphabet(self) -> list[str]:
        alphabet = []
        with open(self.java_name()) as p:
            contents = p.read()
            s = re.findall(r'String\[\] inputs = \{(.*)\}', contents)
            for l in s:
                alphabet.extend(re.findall(r'"([^"]+)"', l))
                print('alphabet', alphabet)
        return alphabet

    def process_input(self, inputs: str | list[str]):
        key = self.current + ''.join(inputs)
        if key in self.mem:
            # assert False
            return self.mem[key]
        assert self.sub is not None
        assert self.sub.stdout is not None
        assert self.sub.stdin is not None
        # assert self.sub.stderr is not None
        if not isinstance(inputs, Iterable):
            inputs = [inputs]
        last_output = ''
        # print(f'running input "{inputs}"')
        for i in inputs:
            a: str = i+'\n'
            self.current += i
            # print('a', a)
            # print('i', a, self.sub.communicate(a))
            # prnt(a)
            # print(self.sub.returncode)
            self.sub.stdin.write(a)
            self.sub.stdin.flush()
            # self.sub.stdout.flush()
            # self.sub.stderr.flush()
            if self.sub.stdout.readable():
                # print('stdout')
                last_output = self.sub.stdout.readline()
            # elif self.sub.stderr.readable():
            #     print('stderr', self.sub.stderr.closed)
            #     last_output = self.sub.stderr.readline()
            else:
                assert False
            self.mem[self.current] = last_output
        # print('output', last_output)
        if last_output.startswith("Invalid"):
            return "invalid_input"
        return last_output

    def reset(self):
        self.current = ''
        self.start()
        # print(self.mem)


def main():
    print('asdf')
    problem = RERS_Problem("compiled", "Problem2")
    eqc = WmethodEquivalenceChecker(sul=problem, m=20)
    # eqc = BFEquivalenceChecker(sul=problem, max_depth=1)
    teacher = Teacher(problem, eqc)
    learner = LStarDFALearner(teacher)
    learner.run(show_intermediate=True)

if __name__ == '__main__':
    main()
