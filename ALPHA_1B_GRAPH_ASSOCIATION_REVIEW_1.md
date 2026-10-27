# Independent review 1 - operational graph crossings and braking

Reviewer: separate read-only agent `review_graph_operations`, 27 October 2026.
Scope: shared resolver/location, front/rear enforcement, copied-route advance
scouting, stale state and regression risks. Reviewer did not edit files.

Initial findings:

1. Old pending rear boundaries used block-centre targets; comparing them directly
   with the new centreline would prevent their release. Fixed by verified legacy
   target normalisation, with a regression and relink rejection check.
2. Unbounded topology inference could infer an impossible long movement or let a
   long loop veto a direct crossing. Fixed with bounded compatible production
   traversal, bounded same-edge movement, stale-sample checks and unknown-path
   rejection. Direct headless adjacency overload is distinguished from production.
3. Manual scout passed movement as Create's UP argument. Fixed to world UP,
   matching installed Create Navigation; manual route tests added.
4. Smoothed transitions need exact offset-aware endpoints, not nominal centre
   projection. Fixed jointly with data reviewer feedback and covered by tests.
5. Removed/wrong-world BEs could be evaluated before old registry cleanup. Both
   front and advance collection now check removal/world membership.
6. Repeated whole-network resolution per board needed indexing. Per-level tick
   indexes remove the repeated B-by-E scans.

Re-review outcome: no remaining release-blocking code findings. Reviewer verified
legacy migration, bounded/live/directional crossings, copied actual routes,
world-UP manual prediction, world guards, indexing and smoothing/client offset
handling. Remaining acceptance limits are live reconstruction, unusual smoothed
layouts and large-world TPS. Final clean build completed after these changes,
with all 862 assertions passing. This is an independent code review, not an
in-game or multiplayer acceptance test.
