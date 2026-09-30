"""Pure helpers for choosing route-local pedestrian-network snaps."""

import math


def select_smoothed_pedestrian_snaps(
    trace: list[tuple[float, float]],
    candidate_layers: list[list[tuple[tuple[int, int], float]]],
    vertices: dict[tuple[int, int], list[float]],
    length_metres,
) -> list[tuple[int, int]] | None:
    """Choose snaps that fit the ordered GPS trace, rather than each fix alone.

    Candidate entries contain a graph vertex and its distance from that GPS
    fix. The transition cost favours progress consistent with observed motion.
    This reduces road/path switching caused by noisy fixes near crossings,
    while retaining turns and out-and-back movement supported by the trace.
    """
    if not trace or len(trace) != len(candidate_layers):
        return None
    if len(trace) == 1:
        return [candidate_layers[0][0][0]] if candidate_layers[0] else None
    if any(not layer for layer in candidate_layers):
        return None

    # Squared error keeps the chosen route close to GPS, while transitions
    # discourage sideways jumps and immediate backtracking.
    def emission_cost(gap: float) -> float:
        return (gap / 15.0) ** 2

    previous_costs = {
        index: emission_cost(gap)
        for index, (_, gap) in enumerate(candidate_layers[0])
    }
    back_pointers: list[dict[int, int]] = []

    for layer_index in range(1, len(candidate_layers)):
        gps_a, gps_b = trace[layer_index - 1], trace[layer_index]
        observed_distance = length_metres(gps_a, gps_b)
        mean_latitude = math.radians((gps_a[1] + gps_b[1]) / 2.0)
        gps_dx = (gps_b[0] - gps_a[0]) * math.cos(mean_latitude)
        gps_dy = gps_b[1] - gps_a[1]
        gps_norm = (gps_dx * gps_dx + gps_dy * gps_dy) ** 0.5
        current_costs: dict[int, float] = {}
        current_back: dict[int, int] = {}

        for current_index, (current_key, current_gap) in enumerate(candidate_layers[layer_index]):
            current_coord = vertices[current_key]
            best_cost = float("inf")
            best_previous = -1
            for previous_index, previous_cost in previous_costs.items():
                previous_key = candidate_layers[layer_index - 1][previous_index][0]
                previous_coord = vertices[previous_key]
                mean_latitude = math.radians((current_coord[1] + previous_coord[1]) / 2.0)
                dx = (current_coord[0] - previous_coord[0]) * math.cos(mean_latitude)
                dy = current_coord[1] - previous_coord[1]
                candidate_distance = length_metres(previous_coord, current_coord)

                # Allow realistic bends but avoid crossing/parallel candidates
                # that force an implausible change in pace or direction.
                distance_error = abs(candidate_distance - observed_distance)
                transition_cost = distance_error / max(22.0, observed_distance * 0.55)

                # The dot product compares direction without a compass bearing.
                if gps_norm > 0.000025 and candidate_distance > 2.0:
                    candidate_norm = (dx * dx + dy * dy) ** 0.5
                    if candidate_norm > 0:
                        alignment = (dx * gps_dx + dy * gps_dy) / (candidate_norm * gps_norm)
                        alignment = max(-1.0, min(1.0, alignment))
                        transition_cost += 16.0 * (1.0 - alignment)
                        if alignment < -0.35:
                            transition_cost += 2.0

                total = previous_cost + transition_cost + emission_cost(current_gap)
                if total < best_cost:
                    best_cost, best_previous = total, previous_index
            current_costs[current_index] = best_cost
            current_back[current_index] = best_previous
        previous_costs = current_costs
        back_pointers.append(current_back)

    state = min(previous_costs, key=previous_costs.get)
    selected = [state]
    for back in reversed(back_pointers):
        state = back[state]
        if state < 0:
            return None
        selected.append(state)
    selected.reverse()
    return [candidate_layers[index][state][0] for index, state in enumerate(selected)]
