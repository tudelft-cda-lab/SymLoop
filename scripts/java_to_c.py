import argparse
import re


def read_file(filename):
    with open(filename) as f:
        return f.readlines()

def replace_single_str(m):
    assert m
    m = m.groups()[0]
    assert len(m) == 1
    return str(ord(m) - ord('A') + 1)
def to_int(char_str):
    m = re.match(r'"(.)"', char_str)
    return replace_single_str(m)


var_regex = r'[a-zA-Z_][a-zA-Z_0-9]*'
pre = '''#include <stdio.h> 
#include <assert.h>
#include <math.h>
#include <stdlib.h>

extern void __VERIFIER_error(int);
'''


def get_main(inputs):
    inputs = ' && '.join([f'(input != {i})' for i in inputs])
    return f'''
int main()
{{
    // main i/o-loop
    while(1)
    {{
        // read input
        int input;
        scanf("%d", &input);        
        // operate eca engine
        if({inputs})
          return -2;
        calculate_output(input);
    }}
}}'''



def convert(lines):
    text = ''.join(lines)

    text = re.sub(r'public(\s+static)?\s+void', 'void', text)
    text = re.sub(r'private\s+void', 'void', text)
    input_regex = re.compile(
            r'private\s+String\[\]\s+inputs\s*=\s*\{([^\n]+)\};'
            # r'private'
        )
    inputs = input_regex.search(text);
    assert inputs
    inputs = inputs.groups()[0]
    seperate_inputs = list(map(to_int, inputs.split(",")))
    inputs = ','.join(seperate_inputs)
    text = re.sub(input_regex, f'//inputs\n\tint inputs[] = {{{inputs}}};', text)
    text = re.sub(r'"(.)"', replace_single_str, text)
    text = re.sub(rf'({var_regex})\.equals\((\d+)\)', r'(\1 == \2)', text)
    text = re.sub(rf'public String ({var_regex})', r'int \1', text)
    text = re.sub(rf'public (int|boolean) ({var_regex})', r'int \2', text)
    text = re.sub(r'import .*;\n', r'', text)
    text = re.sub(r'public class Problem[^\n]*{\s*static BufferedReader[^;]*;', r'', text)
    text = re.sub(r'}\s*$', '', text)
    text = re.sub(rf'void ({var_regex})\(String ({var_regex})\)', r'void \1(int \2)', text)
    text = re.sub(r'System\.out.println\((\d+)\);', r'printf("%d\\n", \1); fflush(stdout);', text);
    text = re.sub(r'Errors\.__VERIFIER_error\((\d+)\);', r'__VERIFIER_error(\1);', text);
    text = re.sub(r'if\(cf\)\s+throw new[^;]+;', r'if( cf==1 )\n\tfprintf(stderr, "Invalid input: %d\\n", input);',text)
    text = re.sub(r'void main.*$', get_main(seperate_inputs), text, flags=re.DOTALL)
    text = re.sub(r'true', '1', text)
    text = re.sub(r'false', '0', text)
    text = re.sub(r'calculateOutput', 'calculate_output', text)
    return pre + text

if __name__ == '__main__':
    parser = argparse.ArgumentParser(
                        prog = 'Java To C',
                        description = 'Convert RERS challenges from Java to C',
                        epilog = 'NOTE: Experimental')
    parser.add_argument('filename', help='The Java program to convert to C')
    args = parser.parse_args()
    lines = read_file(args.filename)
    convert(lines)
    print(convert(lines))
