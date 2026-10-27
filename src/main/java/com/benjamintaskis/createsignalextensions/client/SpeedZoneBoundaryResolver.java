package com.benjamintaskis.createsignalextensions.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Resolves directional speed-board boundaries on a *known, ordered* track path.
 *
 * Positions are cumulative distances along the same oriented route. A board
 * starts its zone at its linked track position and the next board on that route
 * ends it. This does not infer paths through ambiguous junctions: the caller
 * must first resolve a route using Create's graph and provide only boards on it.
 * The legacy turnout/X flag is cosmetic: every board is a normal boundary.
 */
public final class SpeedZoneBoundaryResolver {
    private SpeedZoneBoundaryResolver() {}

    public record Board(double distance, int speedKmh, boolean turnout) {}
    public record Zone(double start, double end, int speedKmh) {}

    public static List<Zone> resolve(List<Board> boards, double routeLength) {
        if (boards == null || !Double.isFinite(routeLength) || routeLength <= 0) return List.of();
        List<Board> ordered = new ArrayList<>();
        for (Board b : boards) {
            if (b != null && b.speedKmh >= 0 &&
                    Double.isFinite(b.distance) && b.distance >= 0 && b.distance < routeLength)
                ordered.add(b);
        }
        // Java's stable sort preserves input order for coincident boards.
        // Collapse coincident boards before producing zones: a board at the
        // same position supersedes the previous one rather than creating an
        // empty zone or incorrectly extending the previous limit.
        ordered.sort(Comparator.comparingDouble(Board::distance));
        List<Board> distinct = new ArrayList<>();
        for (Board board : ordered) {
            if (!distinct.isEmpty() &&
                    Double.compare(distinct.get(distinct.size() - 1).distance(), board.distance()) == 0)
                distinct.set(distinct.size() - 1, board);
            else distinct.add(board);
        }
        List<Zone> zones = new ArrayList<>();
        for (int i = 0; i < distinct.size(); i++) {
            Board board = distinct.get(i);
            if (board.speedKmh() == 0) continue; // An unrestricted board ends the preceding coloured zone.
            double end = i + 1 < distinct.size() ? distinct.get(i + 1).distance() : routeLength;
            if (end > board.distance()) zones.add(new Zone(board.distance(), end, board.speedKmh()));
        }
        return List.copyOf(zones);
    }
}
