total = 256
for i in range(1, total):
    with open(f"afl/tests/{total-i:02}.bin", "wb") as f:
        print(bytes([i]))
        f.write(bytes([i]))
