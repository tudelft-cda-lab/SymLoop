#!/usr/bin/env python3
import argparse
import os
import subprocess
import re


def get_start_time(directory: str):
    stats_file = os.path.join(directory, 'fuzzer_stats')
    assert os.path.exists(stats_file), 'AFL\'s fuzzer_stats not found'
    print('created at', os.path.getctime(stats_file))
    with open(stats_file) as f:
        start_times = re.findall(r'start_time\s+:\s+(\d+)', f.read())
        assert len(start_times) == 1
        return float(start_times[0])

def get_errors(directory: str, binary: str):
    print('getting errors from', directory)
    assert os.path.isfile(binary), "Binary must be a file"
    assert os.path.isdir(directory), "Directory must be a directory"
    crash_path = os.path.join(directory, 'crashes')
    assert os.path.isdir(crash_path), "AFL output dir must contain a 'crashes' directory"
    for crash_file in os.listdir(crash_path):
        # Skip files tat are not crash files
        if not crash_file.startswith('id:'):
            continue
        crash_file = os.path.join(crash_path, crash_file)
        p = subprocess.Popen([binary], stdin=subprocess.PIPE, stdout=subprocess.PIPE,stderr=subprocess.PIPE, text=True)
        with open(crash_file) as f:
            content = f.read()
            _, err = p.communicate(content)
            for err in re.findall(r'(error_\d+)', err):
                print(f'Found: "{err}" for {content=}')
                yield (err, os.path.getctime(crash_file))


def print_errors(errors, directory, start_time: float):
    error_dict = dict()
    for error, time in errors:
        if error not in error_dict or time < error_dict[error]:
            error_dict[error] = time
    print('\nSummary:')
    errors = set(errors)
    for error, time in sorted(error_dict.items()):
        print(f'{error} found in {time-start_time}')
    print(f'Found {len(error_dict)} unique errors in "{directory}"')


def main():
    parser = argparse.ArgumentParser(description="Utility for finding error codes after running AFL on the RERS challenges")
    parser.add_argument('directory', type=str, help='the output directory of afl')
    parser.add_argument('binary', type=str, help='the binary to run the test files through')
    args = parser.parse_args()
    errors = list(get_errors(args.directory, args.binary))
    start_time = get_start_time(args.directory)
    print_errors(errors, args.directory,start_time)


if __name__ == '__main__':
    main()
