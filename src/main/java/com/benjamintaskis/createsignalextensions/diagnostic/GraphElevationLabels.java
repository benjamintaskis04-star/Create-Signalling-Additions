package com.benjamintaskis.createsignalextensions.diagnostic;

import java.awt.FontMetrics;
import java.awt.Rectangle;
import java.util.*;
import static com.benjamintaskis.createsignalextensions.diagnostic.GraphExportSnapshot.*;

/** PNG-only annotations derived from immutable captured geometry. Never edits the snapshot or JSON. */
final class GraphElevationLabels {
    private GraphElevationLabels() {}
    static final int MAX_LABELS = 64;
    static final int MAX_PLACEMENT_ATTEMPTS = 512;
    private static final double POSITION_EPS = 1e-6, GRADE_EPS = .001;
    record Label(String graph, String id, Point point) {}
    record Placed(Label label, String text, Rectangle box, int anchorX, int anchorZ) {}
    private record NodeKey(String graph, String node) {}
    private record End(Point point, Point tangent, double grade, int rise) {}
    private static final class Join {
        final List<End> ends = new ArrayList<>();
        int incident;
        boolean unavailable;
    }

    static List<Label> find(Snapshot snapshot, String dimension, List<GraphExportFiles.SampledEdge> edges) {
        Map<NodeKey, Join> joins = new HashMap<>();
        List<Label> result = new ArrayList<>();
        for (var item : edges) {
            var edge = item.edge();
            if (edge.fromDimension().equals(dimension)) addEnd(joins, new NodeKey(item.graph(), edge.from()), edge, false, dimension);
            if (edge.toDimension().equals(dimension)) addEnd(joins, new NodeKey(item.graph(), edge.to()), edge, true, dimension);
            var geometry = edge.geometry();
            if (!edge.fromDimension().equals(dimension) || !edge.toDimension().equals(dimension) || !geometry.available()) continue;
            if (geometry.controlA() != null) {
                // A cubic's continuous gradient is not divided into arbitrary sampled sections.
                // Only interior vertical extrema (simple roots of dy/dt) are slope boundaries.
                for (double t : extrema(geometry)) result.add(new Label(item.graph(), "edge:" + edge.id() + ":" + t, geometry.at(t)));
            }
        }
        Map<NodeKey, Integer> degrees = new HashMap<>();
        for (var graph : snapshot.graphs()) for (var node : graph.nodes())
            if (node.dimension().equals(dimension)) degrees.put(new NodeKey(graph.id(), node.id()), node.degree());
        for (var entry : joins.entrySet()) {
            var join = entry.getValue();
            // An unknown continuation cannot establish the beginning/end of a slope.
            if (join.unavailable || join.incident != degrees.getOrDefault(entry.getKey(), -1)
                    || join.ends.isEmpty() || join.ends.size() > 16) continue;
            Point point = join.ends.getFirst().point();
            if (join.ends.stream().anyMatch(e -> e.point().minus(point).length() > POSITION_EPS)) continue;
            boolean boundary = join.ends.size() == 1 && join.ends.getFirst().rise() != 0;
            for (int i = 0; i < join.ends.size() && !boundary; i++) for (int j = i + 1; j < join.ends.size(); j++) {
                var a = join.ends.get(i); var b = join.ends.get(j);
                // At a junction, compare through-going directions, not two branches leaving on the same side.
                if (join.ends.size() > 2 && a.tangent().x()*b.tangent().x() + a.tangent().z()*b.tangent().z() >= 0) continue;
                if (transition(a, b)) { boundary = true; break; }
            }
            if (boundary) result.add(new Label(entry.getKey().graph(), "node:" + entry.getKey().node(), point));
        }
        result.sort(Comparator.comparing(Label::graph).thenComparing(Label::id));
        return List.copyOf(result);
    }

    private static void addEnd(Map<NodeKey, Join> joins, NodeKey key, Edge edge, boolean reverse, String dimension) {
        var join = joins.computeIfAbsent(key, k -> new Join()); join.incident++;
        if (!edge.fromDimension().equals(dimension) || !edge.toDimension().equals(dimension) || !edge.geometry().available()) {
            join.unavailable = true; return;
        }
        var end = end(edge.geometry(), reverse);
        if (end == null) join.unavailable = true; else join.ends.add(end);
    }

    private static End end(Geometry geometry, boolean reverse) {
        Point start = reverse ? geometry.end() : geometry.start();
        Point end = reverse ? geometry.start() : geometry.end();
        Point a = geometry.controlA() == null ? end : reverse ? geometry.controlB() : geometry.controlA();
        Point b = geometry.controlB() == null ? end : reverse ? geometry.controlA() : geometry.controlB();
        if (start == null || end == null || !start.finite() || !end.finite() || !a.finite() || !b.finite()) return null;
        // First nonzero control displacement gives the limiting tangent, even for coincident controls.
        Point tangent = a.minus(start);
        if (tangent.length() < POSITION_EPS) tangent = b.minus(start);
        if (tangent.length() < POSITION_EPS) tangent = end.minus(start);
        double horizontal = Math.hypot(tangent.x(), tangent.z());
        if (horizontal < POSITION_EPS) return null; // Vertical/degenerate geometry has no reliable running gradient.
        int rise = 0;
        for (var p : new Point[]{a, b, end}) if (Math.abs(p.y() - start.y()) > POSITION_EPS) {
            rise = p.y() > start.y() ? 1 : -1; break;
        }
        return new End(start, tangent, tangent.y()/horizontal, rise);
    }

    private static boolean transition(End a, End b) {
        if (a.rise() == 0 && b.rise() == 0) return false;
        if (a.rise() == 0 || b.rise() == 0 || a.rise() == b.rise()) return true;
        // Tangents point OUT of the node; a continuous gradient therefore has opposite signs.
        return Math.abs(a.grade() + b.grade()) > GRADE_EPS;
    }

    private static List<Double> extrema(Geometry g) {
        double start = g.start().y(), a = g.controlA().y(), b = g.controlB().y(), end = g.end().y();
        double qa = 3*(-start + 3*a - 3*b + end), qb = 6*(start - 2*a + b), qc = 3*(a - start);
        double scale = Math.max(Math.abs(qa), Math.max(Math.abs(qb), Math.abs(qc)));
        if (scale < POSITION_EPS) return List.of();
        qa /= scale; qb /= scale; qc /= scale;
        List<Double> roots = new ArrayList<>(2);
        if (Math.abs(qa) < 1e-12) {
            if (Math.abs(qb) > 1e-12) roots.add(-qc/qb);
        } else {
            double discriminant = qb*qb - 4*qa*qc;
            if (discriminant > 1e-12) {
                double q = -.5*(qb + Math.copySign(Math.sqrt(discriminant), qb));
                roots.add(q/qa); roots.add(qc/q);
            }
        }
        return roots.stream().filter(t -> Double.isFinite(t) && t > 1e-7 && t < 1-1e-7).sorted().toList();
    }

    static List<Placed> place(List<Label> labels, GraphExportFiles.Projection projection, FontMetrics metrics, Rectangle map) {
        List<Placed> placed = new ArrayList<>();
        int attempted = 0;
        for (var label : labels) {
            if (placed.size() == MAX_LABELS) break;
            double px = projection.x(label.point().x()), pz = projection.z(label.point().z());
            if (!map.contains(px, pz)) continue;
            if (attempted++ == MAX_PLACEMENT_ATTEMPTS) break;
            int x = (int)Math.round(px), z = (int)Math.round(pz);
            String text = String.format(Locale.ROOT, "Y=%.2f", Math.abs(label.point().y()) < 1e-9 ? 0 : label.point().y());
            int width = metrics.stringWidth(text) + 6, height = metrics.getHeight() + 2;
            Rectangle box = null;
            // Bounded search around the exact anchor. Leader lines keep displaced labels unambiguous.
            for (int ring = 0; ring < 4 && box == null; ring++) {
                int gap = 6 + ring*(height + 3);
                int[][] choices = {{x+gap,z-height-gap},{x-width-gap,z-height-gap},{x+gap,z+gap},{x-width-gap,z+gap},
                        {x-width/2,z-height-gap},{x-width/2,z+gap},{x+gap,z-height/2},{x-width-gap,z-height/2}};
                for (var choice : choices) {
                    var candidate = new Rectangle(choice[0], choice[1], width, height);
                    var padding = new Rectangle(candidate); padding.grow(2, 2);
                    if (map.contains(candidate) && placed.stream().noneMatch(p -> p.box().intersects(padding))) { box = candidate; break; }
                }
            }
            if (box != null) placed.add(new Placed(label, text, box, x, z));
        }
        return List.copyOf(placed);
    }
}
