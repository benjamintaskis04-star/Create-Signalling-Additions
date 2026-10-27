# Independent review 2 - resolver, persistence, visuals and diagnostics

Reviewer: separate read-only agent `review_graph_data_visuals`, 27 October 2026.
Scope: ambiguity, loaded anchors, graph rebuilds, visual index/sync/client lookup,
diagnostic limits, networking and resolver cost. Reviewer did not edit files.

Initial findings:

1. Smoothed track retains nominal shape axes while graph endpoints are raised.
   Fixed tilted binding using nominal endpoints plus Create getYOffsetAt and exact
   node lookup. Avoided getConnected walks whose material lookup can touch adjacent
   chunks. Added exact-offset endpoint regression.
2. Client reconstruction through new TrackNodeLocation(Vec3) discarded smoothing
   offsets. Fixed exact dimension/coordinate matching with ambiguity rejection,
   preserving the current packet format. Added a nonzero-offset regression.
3. Per-board network scans and one global cache level caused repeated work and
   cross-dimension thrashing. Fixed per-level tick caches and graph line/curve
   indexes, built once per level/tick.
4. Minor obsolete imports/X-branch wording in the visual data class were removed.

Re-review outcome: all material findings addressed; no remaining material issue
in resolver/data/visual/diagnostic/network scope. Reviewer inspected the new
56-check graph suite and successful run. Unloaded/ambiguous links intentionally
remain unresolved. No packet format update is required. Large-world performance,
live multiplayer and visual acceptance are still unverified.

Final full clean build passed 862 assertions. This is independent read-only
review evidence, not a claim that the supplied in-game crossover was reproduced.
