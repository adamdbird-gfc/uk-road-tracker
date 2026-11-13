package com.roadprints.capture;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

/** Generates connected route hypotheses, then scores the observations against each physical route. */
final class NationalRailway {
    interface Graph {
        List<Station> stations(RailwayMatcher.Fix fix, double radius);
        List<Track> adjacent(long node);
        double roadResidual(RailwayMatcher.Fix fix);
    }

    record Station(long id, long node, String name) {}
    record Track(RailwayMatcher.Edge edge, boolean tunnel) {}
    private record Search(long node, Search previous, Track via, double cost, double priority) {}
    private record Path(List<Track> tracks, Station from, Station to, double metres) {}
    private record Cell(int x, int y) {}
    private record Segment(Track track, double offset, boolean forward) {}
    private record Point(double residual, double offset, boolean tunnel) {}
    private record Scored(Path path, NetworkFirstRailway.EvidenceScore evidence) {}
    // A national diversion around a tunnel can settle more than 60,000 nodes.
    // Keep the discovery and whole-journey budgets independent of this cap.
    private static final int MAX_NODES = 70_000;
    private static final int MAX_SEARCHES = 12;
    private static final int GRID = 256;

    private final Graph graph;

    NationalRailway(Graph graph) { this.graph = graph; }

    static boolean supports(List<RailwayMatcher.Fix> fixes,long start,long end) {
        return fixes.size()>=4 && fixes.size()<=RailwayMatcher.MAX_FIXES
                && end-start>=120_000 && end-start<=12*60*60_000L
                && NetworkFirstRailway.endpointFix(fixes,start,end,false)!=null
                && NetworkFirstRailway.endpointFix(fixes,start,end,true)!=null;
    }

    static NetworkFirstRailway.Outcome unavailableEndpoints() {
        return new NetworkFirstRailway.Outcome("unresolved","missing_endpoint_observation",
                "","",0,0,"","",0,0,0);
    }

    NetworkFirstRailway.Outcome infer(List<RailwayMatcher.Fix> fixes, long start, long end,
                                      BooleanSupplier stop) throws Exception {
        long started = System.nanoTime();
        if (!supports(fixes,start,end)) return unavailableEndpoints();
        var first = NetworkFirstRailway.endpointFix(fixes,start,end,false);
        var last = NetworkFirstRailway.endpointFix(fixes,start,end,true);
        var origins = endpointStations(first,stop);
        var targets = endpointStations(last,stop);
        if (origins.isEmpty() || targets.isEmpty())
            return result("partial","missing_station_attachment",null,0,started,0,0);
        double maxDistance = Math.min(1_350_000,(end-start)*.065+500);
        var candidates = new ArrayList<Path>();
        Set<List<Long>> seen = new HashSet<>();
        Set<Cell> observed = new HashSet<>();
        for (var fix : fixes) {
            if (NetworkFirstRailway.usable(fix,start,end)) observed.add(cell(fix.x(),fix.y()));
        }
        var budget = new SearchBudget();
        try {
            // First search follows travel evidence where available. The second
            // uses neutral costs to challenge it with a physically shorter route.
            add(candidates,seen,search(origins,targets,last,maxDistance,observed,Set.of(),budget,stop));
            add(candidates,seen,search(origins,targets,last,maxDistance,Set.of(),Set.of(),budget,stop));
            if (candidates.isEmpty())
                return result("unresolved","passenger_topology_gap",null,0,started,0,0);
            // Challenge the leading route with deviations at actual junctions.
            // Exclude all nearby parallel tracks in the diversion window so a
            // second platform or running track cannot hide a different branch.
            var primary = candidates.get(0);
            var diversionCells = diversionWindows(primary,observed,stop);
            if (diversionCells.size() > MAX_SEARCHES-2)
                return result("ambiguous","route_alternative_search_limit",null,0,started,0,candidates.size());
            for (var excluded : diversionCells)
                add(candidates,seen,search(origins,targets,last,maxDistance,observed,excluded,budget,stop));
        } catch (SearchLimit failure) {
            return result("unresolved","national_search_limit",null,0,started,0,candidates.size());
        }
        long candidateMs = elapsed(started);
        var plausible = new ArrayList<Scored>();
        var road = new HashMap<Integer,Double>();
        for (Path path : candidates) {
            check(stop);
            var score = score(path,fixes,start,end,road,stop);
            if (score.plausible()) plausible.add(new Scored(path,score));
        }
        if (plausible.isEmpty())
            return result("partial","network_evidence_insufficient",null,0,started,candidateMs,candidates.size());
        var selected = plausible.get(0);
        for (var alternative : plausible) {
            if (!sameCorridor(selected.path(),alternative.path(),stop))
                return result("ambiguous","multiple_rail_routes",null,0,started,candidateMs,candidates.size());
            if (alternative.evidence().compareTo(selected.evidence()) < 0) selected = alternative;
        }
        return result("inferred","network_route_inferred",selected.path(),selected.evidence().witnesses(),
                started,candidateMs,candidates.size());
    }

    private List<Station> endpointStations(RailwayMatcher.Fix fix,BooleanSupplier stop) {
        var supported = new ArrayList<Station>();
        for (var station : graph.stations(fix,550)) {
            check(stop);
            var tracks = graph.adjacent(station.node());
            if (!tracks.isEmpty() && atAttachment(fix,tracks.get(0).edge(),station.node()))
                supported.add(station);
        }
        return supported;
    }

    private static boolean atAttachment(RailwayMatcher.Fix fix,RailwayMatcher.Edge edge,long node) {
        double x = edge.a()==node ? edge.ax() : edge.bx();
        double y = edge.a()==node ? edge.ay() : edge.by();
        return Math.hypot(fix.x()-x,fix.y()-y) <= Math.max(85,3*fix.accuracy());
    }

    private static void add(List<Path> paths,Set<List<Long>> seen,Path path) {
        if (path != null && seen.add(path.tracks().stream().map(t -> t.edge().id()).collect(Collectors.toList()))) paths.add(path);
    }

    private Path search(List<Station> origins,List<Station> targets,RailwayMatcher.Fix end,double limit,
                        Set<Cell> observed,Set<Cell> excluded,SearchBudget budget,BooleanSupplier stop) {
        var goals = new HashMap<Long,Station>();
        targets.forEach(s -> goals.putIfAbsent(s.node(),s));
        var starts = new HashMap<Long,Station>();
        var pending = new PriorityQueue<Search>(Comparator.comparingDouble(Search::priority)
                .thenComparingLong(Search::node));
        var best = new HashMap<Long,Double>();
        for (var origin : origins) {
            if (goals.containsKey(origin.node()) || starts.putIfAbsent(origin.node(),origin) != null) continue;
            best.put(origin.node(),0.0);
            pending.add(new Search(origin.node(),null,null,0,0));
        }
        int settled = 0;
        while (!pending.isEmpty()) {
            if ((settled & 31) == 0) check(stop);
            Search current = pending.remove();
            if (current.cost() != best.get(current.node())) continue;
            if (++settled > MAX_NODES || --budget.remaining < 0) throw new SearchLimit();
            Station target = goals.get(current.node());
            if (target != null && current.via() != null) {
                List<Track> path = new ArrayList<>();
                double metres = 0;
                while (current.previous() != null) {
                    path.add(current.via());
                    metres += current.via().edge().length();
                    current = current.previous();
                }
                Collections.reverse(path);
                return metres <= limit ? new Path(path,starts.get(current.node()),target,metres) : null;
            }
            for (var track : graph.adjacent(current.node())) {
                var e = track.edge();
                boolean forward = e.a() == current.node();
                long node = forward ? e.b() : e.a();
                double x = forward ? e.bx() : e.ax();
                double y = forward ? e.by() : e.ay();
                Cell cell = midpoint(e);
                if (excluded.contains(cell)) continue;
                double cost = current.cost()+e.length()*(observed.isEmpty() || observed.contains(cell) ? 1 : 1.35);
                // Subtract the largest endpoint offset before bounding A*.
                double remaining = Math.max(0,Math.hypot(x-end.x(),y-end.y())*.997-1100);
                if (cost+remaining > limit*1.35 || cost >= best.getOrDefault(node,Double.POSITIVE_INFINITY)) continue;
                if (best.size() >= 75_000) throw new SearchLimit();
                best.put(node,cost);
                pending.add(new Search(node,current,track,cost,cost+remaining));
            }
        }
        return null;
    }

    private List<Set<Cell>> diversionWindows(Path path,Set<Cell> observed,BooleanSupplier stop) {
        List<Set<Cell>> result = new ArrayList<>();
        Set<Long> pathNodes = new HashSet<>();
        path.tracks().forEach(t -> { pathNodes.add(t.edge().a()); pathNodes.add(t.edge().b()); });
        // Only challenge gaps large enough to conceal a distinct physical
        // route. A GPS witness on both sides of a switch disambiguates that switch.
        long node = path.from().node();
        int gapStart = 0;
        double gapMetres = 0;
        boolean branches = false;
        for (int i=0; i<path.tracks().size(); i++) {
            check(stop);
            var edge = path.tracks().get(i).edge();
            if (observed.contains(midpoint(edge))) {
                addDiversionWindow(path,gapStart,i,gapMetres,branches,result);
                gapStart = i+1;
                gapMetres = 0;
                branches = false;
            } else {
                gapMetres += edge.length();
                branches |= graph.adjacent(node).stream().anyMatch(t ->
                        !pathNodes.contains(t.edge().a()) || !pathNodes.contains(t.edge().b()));
            }
            node = edge.a()==node ? edge.b() : edge.a();
        }
        addDiversionWindow(path,gapStart,path.tracks().size(),gapMetres,branches,result);
        return result;
    }

    private static void addDiversionWindow(Path path,int start,int end,double metres,boolean branches,
                                           List<Set<Cell>> result) {
        if (!branches || metres < 400) return;
        Set<Cell> window = new HashSet<>();
        double offset = 0;
        for (int i=start; i<end; i++) {
            var edge = path.tracks().get(i).edge();
            offset += edge.length();
            if (offset < metres*.25) continue;
            window.add(midpoint(edge));
            if (offset >= metres*.75) break;
        }
        if (!window.isEmpty()) result.add(window);
    }

    private NetworkFirstRailway.EvidenceScore score(Path path,List<RailwayMatcher.Fix> fixes,long start,long end,
                                                    Map<Integer,Double> road,BooleanSupplier stop) {
        var grid = index(path);
        var evidence = new NetworkFirstRailway.Evidence(start,end,path.metres());
        for (int i=0; i<fixes.size(); i++) {
            check(stop);
            var fix = fixes.get(i);
            if (!NetworkFirstRailway.usable(fix,start,end)) continue;
            Point nearest = nearest(grid,fix);
            double roadResidual = nearest.residual() <= 12 && !nearest.tunnel()
                    ? road.computeIfAbsent(i, ignored -> graph.roadResidual(fix)) : 0;
            evidence.add(fix,i,nearest.residual(),nearest.offset(),nearest.tunnel(),roadResidual);
        }
        return evidence.finish();
    }

    private static Map<Cell,List<Segment>> index(Path path) {
        Map<Cell,List<Segment>> grid = new HashMap<>();
        long node = path.from().node();
        double offset = 0;
        for (Track track : path.tracks()) {
            var e = track.edge();
            boolean forward = e.a()==node;
            var segment = new Segment(track,offset,forward);
            for (int x=cell(Math.min(e.ax(),e.bx())-124,0).x();x<=cell(Math.max(e.ax(),e.bx())+124,0).x();x++)
                for (int y=cell(0,Math.min(e.ay(),e.by())-124).y();y<=cell(0,Math.max(e.ay(),e.by())+124).y();y++)
                    grid.computeIfAbsent(new Cell(x,y),ignored->new ArrayList<>()).add(segment);
            node = forward ? e.b() : e.a();
            offset += e.length();
        }
        return grid;
    }

    private static Point nearest(Map<Cell,List<Segment>> grid,RailwayMatcher.Fix fix) {
        var nearest = new Point(124,0,true);
        for (var segment : grid.getOrDefault(cell(fix.x(),fix.y()),List.of())) {
            var hit = segment.track().edge().hit(fix);
            if (hit.residual() >= nearest.residual()) continue;
            double fraction = segment.forward() ? hit.fraction() : 1-hit.fraction();
            nearest = new Point(hit.residual(),segment.offset()+fraction*hit.edge().length(),segment.track().tunnel());
        }
        return nearest;
    }

    private static boolean sameCorridor(Path first,Path second,BooleanSupplier stop) {
        if (first.from().id()!=second.from().id() || first.to().id()!=second.to().id()
                || Math.abs(first.metres()-second.metres()) > 250) return false;
        return separatedMetres(first,index(second),stop)<250 && separatedMetres(second,index(first),stop)<250;
    }

    private static double separatedMetres(Path path,Map<Cell,List<Segment>> other,BooleanSupplier stop) {
        double separated = 0;
        for (Track track : path.tracks()) {
            check(stop);
            var e = track.edge();
            var middle = new RailwayMatcher.Fix((e.ax()+e.bx())/2,(e.ay()+e.by())/2,0,0);
            if (nearest(other,middle).residual()>30) separated += e.length();
            if (separated>=150) break;
        }
        return separated;
    }

    private static NetworkFirstRailway.Outcome result(String confidence,String reason,Path path,int witnesses,
                                                       long started,long candidateMs,int paths) throws Exception {
        String endpoints = "", digest = "", refs = "";
        double metres = 0;
        if (path != null) {
            var hash = MessageDigest.getInstance("SHA-256");
            var lines = new LinkedHashSet<String>();
            long node = path.from().node();
            for (var track : path.tracks()) {
                var edge = track.edge();
                hash.update((edge.id()+":"+node+",").getBytes(StandardCharsets.US_ASCII));
                if (!edge.ref().isEmpty()) lines.add(edge.ref());
                node = node==edge.a() ? edge.b() : edge.a();
            }
            endpoints = path.from().name()+" → "+path.to().name();
            refs = String.join(", ",lines.stream().limit(24).collect(Collectors.toList()));
            digest = RegionalRailwayCatalogue.hex(hash.digest());
            metres = path.metres();
        }
        return new NetworkFirstRailway.Outcome(confidence,reason,refs,"",metres,witnesses,endpoints,digest,
                candidateMs,Math.max(0,elapsed(started)-candidateMs),paths);
    }

    private static long elapsed(long start) { return (System.nanoTime()-start)/1_000_000; }
    private static Cell cell(double x,double y) { return new Cell((int)Math.floor(x/GRID),(int)Math.floor(y/GRID)); }
    private static Cell midpoint(RailwayMatcher.Edge e) { return cell((e.ax()+e.bx())/2,(e.ay()+e.by())/2); }
    private static void check(BooleanSupplier stop) { if (stop.getAsBoolean()) throw new CancellationException(); }
    private static final class SearchBudget { int remaining = 180_000; }
    private static final class SearchLimit extends RuntimeException {}
}
