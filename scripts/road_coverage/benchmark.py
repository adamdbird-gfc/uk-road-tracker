"""Measure geometry preparation for a controlled repeated-route workload."""

import json
from statistics import median
from time import perf_counter
import tracemalloc

from .ledger import Ledger, assess
from .network import Edge, Fix, Network
from .replay import EXTRACT

JOURNEYS = 1664


def timed(action, repetitions):
    samples_ms = []
    for _ in range(repetitions):
        start = perf_counter()
        action()
        samples_ms.append((perf_counter() - start) * 1000)
    return round(median(samples_ms), 4)


def peak_bytes(action):
    tracemalloc.start()
    try:
        result = action()
        memory = tracemalloc.get_traced_memory()
        del result
        return memory
    finally:
        tracemalloc.stop()


def run():
    local = Network("benchmark", [
        Edge(f"edge-{i}", i, i + 1, (i * 60, 0), ((i + 1) * 60, 0), way=1)
        for i in range(16)
    ])
    points = [(i * 30, 0) for i in range(33)]
    samples = [Fix(point, i * 1500, 2) for i, point in enumerate(points)]

    def contribution():
        return assess(local, samples, confirmed_driving=True, reference_in_period=True)

    def prepare_legacy():
        return [[local.lonlat(point) for point in points] for _ in range(JOURNEYS)]

    def prepare_network():
        return list(ledger.strokes())

    def populate():
        result = Ledger(local)
        for journey in range(JOURNEYS):
            result.replace(str(journey), contribution())
        return result

    start = perf_counter()
    ledger = populate()
    full_ms = (perf_counter() - start) * 1000

    cached = contribution()
    next_journey = [JOURNEYS]

    def update_one():
        next_journey[0] += 1
        journey = str(next_journey[0])
        ledger.replace(journey, cached)
        ledger.replace(journey, None)

    calls = {}
    for label, action in (("per_journey_lines", prepare_legacy), ("network_strokes", prepare_network)):
        prepared = action()
        _, peak = peak_bytes(action)
        calls[label] = {
            "paths": len(prepared),
            "vertices": sum(len(p if isinstance(p, list) else p["coordinates"]) for p in prepared),
            "preparation_median_ms": timed(action, 11),
            "preparation_peak_python_bytes": peak,
        }
    _, extract_peak = peak_bytes(lambda: Network.load(EXTRACT))
    ledger_retained, ledger_peak = peak_bytes(populate)
    return {
        "workload": "1,664 identical synthetic 960 m journeys over a 16-edge road; coverage already assessed for warm prep",
        "cold_assess_and_aggregate_ms": round(full_ms, 2),
        "pair_assessment_median_ms_per_journey": timed(contribution, 31),
        "cached_add_and_delete_median_ms": timed(update_one, 101),
        "ledger_retained_python_bytes": ledger_retained,
        "ledger_build_peak_python_bytes": ledger_peak,
        "gravesend_extract_gzip_bytes": EXTRACT.stat().st_size,
        "gravesend_load_median_ms": timed(lambda: Network.load(EXTRACT), 11),
        "gravesend_load_peak_python_bytes": extract_peak,
        **calls,
    }


if __name__ == "__main__":
    print(json.dumps(run(), indent=2))
