import ctypes

M    = 0x5DEECE66D
A    = 0xB
MASK = (1 << 48) - 1

seed = 42
seed = (seed ^ M) & MASK
print(f"After setSeed(42): seed = {seed} (hex: {hex(seed)})")

# First next(32) — Java's nextInt() with no bound
seed = (seed * M + A) & MASK
print(f"After first step:  seed = {seed} (hex: {hex(seed)})")
bits32 = ctypes.c_int32(seed >> 16).value
print(f"nextInt() = seed >> 16 as int32 = {bits32}")
print(f"Expected Java:                    -1155484576")
print()

# Independently verify expected
# Java Random(42) nextInt() should be -1155484576 according to Java spec
# Let us compute manually:
# seed0 = (42 ^ 0x5DEECE66D) & MASK = 42 XOR 25214903917
seed0 = 42 ^ 25214903917
print(f"seed0 = 42 XOR 25214903917 = {seed0} = {hex(seed0)}")
seed1 = (seed0 * M + A) & MASK
print(f"seed1 = (seed0*M + A) & MASK = {seed1} = {hex(seed1)}")
print(f"nextInt = (int)(seed1 >>> 16) = {ctypes.c_int32(seed1 >> 16).value}")
