"""Executable CPU-side regression check for the Android probe classification."""
import numpy as np

a = np.float32(.1) + np.float32(.37) * np.arange(64, dtype=np.float32)
b = a * np.float32(.5) + np.float32(1)
rounded = a * b
want = (a.astype(np.float64) * b.astype(np.float64) - rounded.astype(np.float64)).astype(np.float32)
assert int(np.count_nonzero(want)) == 62
assert abs(float(want[0]) - 7.45057349149647e-11) < 1e-20
print("probe reference lanes=64 nonzero_want=62; representative_want=%.17g" % want[0])
