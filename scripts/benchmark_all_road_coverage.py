"""Measure repeat-heavy desktop projection and geometry preparation, without owner data."""

from statistics import median
import json
from time import perf_counter
import tracemalloc

from all_road_coverage import Edge, Fix, Ledger, Network, observed_coverage
from replay_all_road_coverage import REFERENCE, coverage_features, feature, geometry_counts

JOURNEYS = 1664
BOUNDS = {"west": 0.35, "south": 51.425, "north": 51.445}


def repeated_corridor():
    network = Network("synthetic", [
        Edge(f"osm:1:{i}:{i + 1}", (str(i), str(i + 1)), "Synthetic street",
             (25 * i, 0), (25 * (i + 1), 0))
        for i in range(20)
    ], BOUNDS)
    fixes = [Fix((25 * i, 0), 2_000 * i, 2) for i in range(21)]
    spans, holds = observed_coverage(network, fixes)
    assert not holds
    ledger = Ledger(network)
    started = perf_counter()
    for i in range(JOURNEYS):
        ledger.replace(str(i), "prepared", lambda: spans)
    ingest_ms = (perf_counter() - started) * 1000

    started = perf_counter()
    legacy = [feature([[point / 100_000, 51.425] for point, _ in (fix.point for fix in fixes)])
              for _ in range(JOURNEYS)]
    legacy_ms = (perf_counter() - started) * 1000
    started = perf_counter()
    cumulative = list(coverage_features(ledger, "covered"))
    cumulative_ms = (perf_counter() - started) * 1000

    started = perf_counter()
    ledger.replace("new", "prepared", lambda: spans)
    append_ms = (perf_counter() - started) * 1000
    assert ledger.unique_mm == 500_000
    return {
        "legacy": geometry_counts(legacy), "cumulative": geometry_counts(cumulative),
        "legacy_geometry_ms": legacy_ms, "cumulative_geometry_ms": cumulative_ms,
        "ingest_prepared_spans_ms": ingest_ms, "append_repeat_ms": append_ms,
    }


def main():
    repetitions = [repeated_corridor() for _ in range(5)]
    report = {key: median(result[key] for result in repetitions) if key.endswith("_ms")
              else value for key, value in repetitions[0].items()}
    times = []
    for _ in range(5):
        started = perf_counter()
        network = Network.gravesend(REFERENCE)
        times.append((perf_counter() - started) * 1000)
        del network
    report["gravesend_catalogue_ms"] = median(times)

    tracemalloc.start()
    catalogue = Network.gravesend(REFERENCE)
    current, peak = tracemalloc.get_traced_memory()
    tracemalloc.stop()
    assert catalogue[0].edges
    report.update(gravesend_catalogue_retained_bytes=current, gravesend_catalogue_peak_bytes=peak)
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
