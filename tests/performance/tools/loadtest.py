#!/usr/bin/env python3
"""Minimal HTTP load test (stdlib only).
Usage: python3 loadtest.py <url> <threads> <duration_seconds> <csv_out>
Prints JSON summary: requests, throughput, avg_ms, p95_ms, error_rate.
"""
import json
import statistics
import sys
import threading
import time
import urllib.request

url, threads, duration, csv_out = sys.argv[1], int(sys.argv[2]), int(sys.argv[3]), sys.argv[4]
latencies = []   # ms
errors = [0]
stop = time.time() + duration
lock = threading.Lock()


def worker():
    while time.time() < stop:
        t0 = time.perf_counter()
        try:
            with urllib.request.urlopen(url, timeout=5) as r:
                r.read()
                code = r.status
        except urllib.error.HTTPError as e:
            code = e.code
        except Exception:
            code = -1
        dt = (time.perf_counter() - t0) * 1000
        with lock:
            latencies.append(dt)
            if code < 200 or code >= 400:
                errors[0] += 1


ts = [threading.Thread(target=worker) for _ in range(threads)]
started = time.time()
for t in ts:
    t.start()
for t in ts:
    t.join()
elapsed = time.time() - started

n = len(latencies)
lat_sorted = sorted(latencies)
p95 = lat_sorted[int(n * 0.95)] if n else 0
summary = {
    "url": url,
    "threads": threads,
    "duration_s": round(elapsed, 1),
    "requests": n,
    "throughput_rps": round(n / elapsed, 2),
    "avg_ms": round(statistics.mean(latencies), 2) if n else 0,
    "p95_ms": round(p95, 2),
    "errors": errors[0],
    "error_rate": round(errors[0] / n, 4) if n else 0,
}
with open(csv_out, "w") as f:
    f.write("latency_ms\n")
    for v in latencies:
        f.write(f"{v:.2f}\n")
print(json.dumps(summary))
