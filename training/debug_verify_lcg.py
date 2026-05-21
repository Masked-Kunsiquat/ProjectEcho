"""
Verify Python JavaRandom matches Java Random by checking well-known values.

Java spec / common references:
  new Random(0).nextInt()  = -1155484576
  new Random(0).nextInt()  = -1155484576  (first call)

If Python gives the same value for seed=0, our LCG is correct.
"""
from simulation import JavaRandom

# Test seed=0
rng0 = JavaRandom(0)
v0 = rng0.next_int()
print(f"Random(0).nextInt() = {v0}")
print("Expected (Java spec) = -1155484576")
print(f"Match: {v0 == -1155484576}")
print()

# Test seed=42
rng42 = JavaRandom(42)
v42 = rng42.next_int()
print(f"Random(42).nextInt() = {v42}")
print()

# Print first 5 values for seed=42 to identify any pattern
rng42_2 = JavaRandom(42)
print("First 5 nextInt() values for seed=42:")
for i in range(5):
    print(f"  [{i}] {rng42_2.next_int()}")

# Print first 5 nextBoolean() values for seed=42
rng42_3 = JavaRandom(42)
print("\nFirst 5 nextBoolean() values for seed=42:")
for i in range(5):
    print(f"  [{i}] {rng42_3.next_bool()}")

# Print first 5 nextFloat() values for seed=42
rng42_4 = JavaRandom(42)
print("\nFirst 5 nextFloat() values for seed=42:")
for i in range(5):
    print(f"  [{i}] {rng42_4.next_float():.8f}")
