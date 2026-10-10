#!/usr/bin/env python3
"""Regenerates src/main/resources/data/dataset-<size>.bin.

Each file is a serialized `Root` protobuf message (Root.data[0] = DataEntry,
DataEntry.dates[0] = DateRecord, DateRecord.a = packed doubles; all field number 1)
holding one synthetic ML *recall trend* of <size> points (training progress 0..1):
fast rise that saturates, a slow late gain, a small epoch ripple, noise that shrinks
as training settles, and occasional short dips. Values are clipped to [0, 1].
Seeded, so every run produces identical files.

Usage: scripts/generate_datasets.py [output_dir]
"""
import math
import random
import struct
import sys
from array import array

SIZES = (10_000, 100_000, 1_000_000, 10_000_000)
SEED = 0


def recall_trend(n: int) -> array:
    rng = random.Random(SEED)
    out = array('d', bytes(8 * n))
    last = max(n - 1, 1)
    for i in range(n):
        t = i / last
        base = 0.94 - 0.82 * math.exp(-7.0 * t) + 0.03 * t
        ripple = 0.012 * (1.0 - t) * math.sin(2.0 * math.pi * 40.0 * t)
        noise = rng.gauss(0.0, 0.035 * (1.0 - 0.85 * t) + 0.004)
        out[i] = base + ripple + noise
    # Sparse dips that recover exponentially (e.g. a bad batch / learning-rate spike).
    window = max(n // 400, 5)
    for _ in range(12):
        at = rng.randrange(n // 20, n)
        depth = rng.uniform(0.05, 0.2)
        for k in range(min(window * 4, n - at)):
            out[at + k] -= depth * math.exp(-k / window)
    for i in range(n):
        out[i] = min(1.0, max(0.0, out[i]))
    return out


def varint(n: int) -> bytes:
    out = bytearray()
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            out.append(b | 0x80)
        else:
            out.append(b)
            return bytes(out)


def length_delimited(payload: bytes) -> bytes:
    return b'\x0a' + varint(len(payload)) + payload  # field 1, wire type 2


def encode_root(values: array) -> bytes:
    if sys.byteorder != 'little':
        values = array('d', values)
        values.byteswap()
    record = length_delimited(values.tobytes())
    entry = length_delimited(record)
    return length_delimited(entry)


if __name__ == '__main__':
    out_dir = sys.argv[1] if len(sys.argv) > 1 else 'src/main/resources/data'
    for size in SIZES:
        data = encode_root(recall_trend(size))
        with open(f'{out_dir}/dataset-{size}.bin', 'wb') as f:
            f.write(data)
        print(size, len(data))
