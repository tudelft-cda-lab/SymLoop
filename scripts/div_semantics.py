
def model(a, b):
    return a // b


def java(a, b):
    return int(a / b)


def sign(x):
    if x == 0:
        return 0
    elif x > 0:
        return 1
    else:
        return -1

def modified(a, b):
    m = model(abs(a), (abs(b)))
    return m * sign(a) * sign(b)


def test(a, b):
    if b == 0:
        return
    funcs = [model, java, modified]
    print(f'{a=:7}, {b=:7},', end="")
    print([f'{f.__name__}: {f(a, b)}' for f in funcs])
    assert java(a, b) == modified(a, b)


amount = 1000
for i in range(-amount, amount):
    for j in range(-amount, amount):
        test(i, j)
