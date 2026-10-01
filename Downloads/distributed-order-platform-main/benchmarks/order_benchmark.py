#!/usr/bin/env python3
"""Reproducible HTTP + end-to-end benchmark for the order platform.

Uses only the Python standard library. It measures:
- POST acceptance throughput and p50/p95/p99 latency
- terminal saga completion (CONFIRMED/CANCELLED/FAILED)
- end-to-end terminal completion throughput
- benchmark environment metadata

Important: the default inventory seed is finite. Keep --orders <= 400 unless you
reset/expand inventory before a run. The script reports terminal completion, not
"uptime".
"""
from __future__ import annotations

import argparse
import concurrent.futures as cf
import datetime as dt
import json
import math
import os
import platform
import statistics
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path

TERMINAL = {"CONFIRMED", "CANCELLED", "FAILED"}


def percentile(values, p):
    if not values:
        return None
    xs = sorted(values)
    if len(xs) == 1:
        return xs[0]
    k = (len(xs) - 1) * p / 100.0
    f, c = math.floor(k), math.ceil(k)
    if f == c:
        return xs[int(k)]
    return xs[f] * (c - k) + xs[c] * (k - f)


def request_json(method, url, payload=None, timeout=10):
    data = None
    headers = {"Accept": "application/json"}
    if payload is not None:
        data = json.dumps(payload).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    start = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            body = resp.read()
            elapsed_ms = (time.perf_counter() - start) * 1000
            obj = json.loads(body.decode("utf-8")) if body else {}
            return resp.status, obj, elapsed_ms, None
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")
        return e.code, None, (time.perf_counter() - start) * 1000, body[:500]
    except Exception as e:
        return 0, None, (time.perf_counter() - start) * 1000, repr(e)


def create_one(base_url, idx, product_id, quantity, unit_price):
    payload = {
        "customerId": f"bench-customer-{idx}-{uuid.uuid4().hex[:8]}",
        "customerEmail": f"bench-{idx}@example.com",
        "items": [{
            "productId": product_id,
            "productName": "Benchmark Product",
            "quantity": quantity,
            "unitPrice": unit_price,
        }],
    }
    status, obj, ms, err = request_json("POST", f"{base_url}/api/v1/orders", payload, timeout=20)
    oid = obj.get("id") if isinstance(obj, dict) else None
    return {"http_status": status, "order_id": oid, "latency_ms": ms, "error": err}


def poll_terminal(base_url, order_id, timeout_s, interval_s):
    deadline = time.monotonic() + timeout_s
    last_status = None
    attempts = 0
    while time.monotonic() < deadline:
        attempts += 1
        status, obj, _, _ = request_json("GET", f"{base_url}/api/v1/orders/{order_id}", timeout=10)
        if status == 200 and isinstance(obj, dict):
            last_status = obj.get("status")
            if last_status in TERMINAL:
                return last_status, attempts
        time.sleep(interval_s)
    return last_status or "TIMEOUT", attempts


def healthcheck(base_url):
    status, obj, ms, err = request_json("GET", f"{base_url}/actuator/health", timeout=5)
    if status != 200:
        raise SystemExit(f"Order service health check failed: HTTP {status}, error={err}, body={obj}")
    return ms


def run(args):
    health_ms = healthcheck(args.url)
    run_id = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    print(f"Health OK ({health_ms:.1f} ms). Run {run_id}")
    print(f"Creating {args.orders} orders with concurrency={args.concurrency}")

    start = time.perf_counter()
    results = []
    with cf.ThreadPoolExecutor(max_workers=args.concurrency) as ex:
        futs = [ex.submit(create_one, args.url, i, args.product_id, args.quantity, args.unit_price)
                for i in range(args.orders)]
        for i, fut in enumerate(cf.as_completed(futs), 1):
            results.append(fut.result())
            if i % max(1, args.orders // 10) == 0:
                print(f"  submitted: {i}/{args.orders}")
    post_elapsed = time.perf_counter() - start

    accepted = [r for r in results if r["http_status"] == 201 and r["order_id"]]
    latencies = [r["latency_ms"] for r in accepted]
    failed_http = len(results) - len(accepted)

    poll_start = time.perf_counter()
    terminal_results = []
    if accepted and not args.no_poll:
        with cf.ThreadPoolExecutor(max_workers=min(args.poll_concurrency, len(accepted))) as ex:
            futs = {ex.submit(poll_terminal, args.url, r["order_id"], args.poll_timeout, args.poll_interval): r["order_id"]
                    for r in accepted}
            for fut in cf.as_completed(futs):
                status, attempts = fut.result()
                terminal_results.append({"order_id": futs[fut], "status": status, "poll_attempts": attempts})
    poll_elapsed = time.perf_counter() - poll_start

    status_counts = {}
    for r in terminal_results:
        status_counts[r["status"]] = status_counts.get(r["status"], 0) + 1
    terminal_count = sum(v for k, v in status_counts.items() if k in TERMINAL)

    summary = {
        "run_id": run_id,
        "environment": {
            "platform": platform.platform(),
            "python": platform.python_version(),
            "cpu_count": os.cpu_count(),
        },
        "config": vars(args),
        "http": {
            "attempted": len(results),
            "accepted_201": len(accepted),
            "failed": failed_http,
            "elapsed_s": round(post_elapsed, 4),
            "accepted_orders_per_s": round(len(accepted) / post_elapsed, 3) if post_elapsed else None,
            "p50_ms": round(percentile(latencies, 50), 3) if latencies else None,
            "p95_ms": round(percentile(latencies, 95), 3) if latencies else None,
            "p99_ms": round(percentile(latencies, 99), 3) if latencies else None,
            "mean_ms": round(statistics.mean(latencies), 3) if latencies else None,
        },
        "saga": {
            "polled": len(terminal_results),
            "terminal_count": terminal_count,
            "terminal_completion_rate_pct": round(100 * terminal_count / len(accepted), 3) if accepted else None,
            "statuses": status_counts,
            "poll_elapsed_s": round(poll_elapsed, 4),
            "terminal_orders_per_s": round(terminal_count / (post_elapsed + poll_elapsed), 3) if (post_elapsed + poll_elapsed) else None,
        },
        "errors_sample": [r["error"] for r in results if r["error"]][:10],
    }

    outdir = Path(args.output_dir)
    outdir.mkdir(parents=True, exist_ok=True)
    outfile = outdir / f"order_benchmark_{run_id}.json"
    outfile.write_text(json.dumps(summary, indent=2))

    print("\n=== RESULT ===")
    print(json.dumps(summary, indent=2))
    print(f"\nSaved: {outfile}")
    return summary


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--url", default="http://localhost:8081")
    p.add_argument("--orders", type=int, default=200)
    p.add_argument("--concurrency", type=int, default=16)
    p.add_argument("--product-id", default="PROD-006", help="PROD-006 has 500 seeded units by default")
    p.add_argument("--quantity", type=int, default=1)
    p.add_argument("--unit-price", type=float, default=9.99)
    p.add_argument("--poll-timeout", type=float, default=30.0)
    p.add_argument("--poll-interval", type=float, default=0.15)
    p.add_argument("--poll-concurrency", type=int, default=32)
    p.add_argument("--no-poll", action="store_true", help="measure POST acceptance only")
    p.add_argument("--output-dir", default="benchmarks/results")
    args = p.parse_args()
    if args.orders < 1 or args.concurrency < 1:
        p.error("--orders and --concurrency must be >= 1")
    run(args)

if __name__ == "__main__":
    main()
