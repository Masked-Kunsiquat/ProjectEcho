import ctypes

def java_hashcode_debug(s):
    h = 0
    for i, c in enumerate(s):
        prev = h
        h = ctypes.c_int32(31 * h + ord(c)).value
        print(f"  [{i}] '{c}' ({ord(c)}): 31*{prev} + {ord(c)} = {31*prev + ord(c)} -> {h}")
    return h

print("Tracing java_hashcode('iron-wrought'):")
result = java_hashcode_debug("iron-wrought")
print(f"Result: {result}")
print(f"Expected: -1184235095")
print(f"Difference: {result - (-1184235095)}")
