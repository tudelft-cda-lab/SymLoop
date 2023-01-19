from z3 import *
x = Int('x')
y = Int('y')
s = Solver()
s.add(x > 2)


def check_free(var):
    assert s.check() == sat
    m = s.model()
    e = m.eval(var)
    return e.eq(var)

print("free before:", check_free(y))
s.add( x < y)
print("free after: ", check_free(y))

import ctypes
a = ctypes.c_ulong(-1)
b = ctypes.c_ulong(1)


print(f'division by zero')
s = Solver()
s.add(x == 0)
s.add(y / x == 1)
print(s)
print(s.check())
print(s.model())
