package com.roadprints.capture;

import org.json.JSONArray;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Single accepted matched-corridor display union for UK unresolved footpaths
 * and worldwide matched routes. Country and travel mode are not part of
 * physical-shape identity. Canonical UK edge intervals are rendered by their
 * existing stronger OSM-ID union, not reintroduced here as journey traces.
 *
 * Only correction-trimmed accepted geometry may be passed to this class.
 * This is a display ledger, not a road or NCN completion ledger.
 */
final class MatchedCorridorAccumulator {
    enum Result { ADDED, REUSED, HELD }
    private MatchedCorridorAccumulator() {}

    static Result add(JSONArray acceptedLine, String journeyId, boolean unresolvedFootpath,
                      List<JSONArray> lines, List<Set<String>> contributorIds,
                      Set<Integer> footpathIndices, Map<String, Integer> identities,
                      int maxNewLines, int maxNewPoints, int currentNewLines,
                      int currentNewPoints) {
        if (acceptedLine == null || acceptedLine.length() < 2
                || journeyId == null || journeyId.isBlank()
                || lines == null || contributorIds == null
                || footpathIndices == null || identities == null
                || lines.size() != contributorIds.size()) return Result.HELD;
        String identity = OverseasMatchedGeometry.displayIdentity(acceptedLine);
        if (identity.isEmpty()) return Result.HELD;
        Integer existing = identities.get(identity);
        if (existing == null && lines.size() <= 500) {
            // Only inspect the bounded display ledger. The cheap endpoint/length
            // gates inside samePhysicalShape reject unrelated physical streets.
            // Do not search the UK canonical network here: it already owns
            // authoritative OSM physical-road identity and credit.
            for (int i = 0; i < lines.size(); i++) {
                if (!MatchedCorridorEquivalence.samePhysicalShape(acceptedLine, lines.get(i)))
                    continue;
                existing = i;
                break;
            }
        }
        if (existing != null) {
            if (existing < 0 || existing >= lines.size()) return Result.HELD;
            // Memoise each new accepted geometry fingerprint so a repeated
            // correction-free journey can take the exact-key fast path.
            identities.put(identity, existing);
            contributorIds.get(existing).add(journeyId);
            if (unresolvedFootpath) footpathIndices.add(existing);
            return Result.REUSED;
        }
        if (currentNewLines >= maxNewLines || acceptedLine.length() > maxNewPoints - currentNewPoints)
            return Result.HELD;
        int index = lines.size();
        identities.put(identity, index);
        lines.add(acceptedLine);
        Set<String> contributors = new HashSet<>();
        contributors.add(journeyId);
        contributorIds.add(contributors);
        if (unresolvedFootpath) footpathIndices.add(index);
        return Result.ADDED;
    }

    static boolean coherent(List<JSONArray> lines, List<Set<String>> contributorIds,
                            Set<Integer> footpathIndices) {
        if (lines == null || contributorIds == null || footpathIndices == null
                || lines.size() != contributorIds.size()) return false;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i) == null || lines.get(i).length() < 2
                    || contributorIds.get(i) == null || contributorIds.get(i).isEmpty()) return false;
        }
        for (Integer index : footpathIndices)
            if (index == null || index < 0 || index >= lines.size()) return false;
        return true;
    }
}
