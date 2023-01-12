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
