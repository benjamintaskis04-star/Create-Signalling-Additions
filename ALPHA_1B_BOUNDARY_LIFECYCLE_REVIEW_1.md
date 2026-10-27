# Boundary lifecycle correction — independent review 1

Date: 27 October 2026 (Australia/Sydney). Baseline: `6eb32911d525660df34241992c28ca3f22d1ce9f`.

An independent read-only reviewer inspected functional correctness, actual Create 6.0.10 tick order, movement direction, graph crossings, delayed recovery, occupied-section semantics and manager integration tests. The reviewer did not edit files, run builds or claim in-game acceptance. This report records that review and subsequent re-review.

Confirmed findings corrected:

- Create passes the overworld into all `Train.tick` calls. Snapshots now use physical travelling-point dimensions and include both ends across a portal.
- Signal/station/blocked movement can set speed to zero before tick RETURN. An exact `Carriage.travel` injection captures the signed movement command once, preserving direction and crossing budget.
- Endpoint samples alone must not infer an old intermediate route from rebuilt topology. Historical/rebased replay now requires an exact observed edge or directly adjacent transition; uncertain intermediate routes remain conservative.
- Focused lifecycle diagnostics are retained under the existing default-off graph diagnostic switch.

The reviewed occupied-section model includes the rear/base and all sections entered by the front until verified rear supersession. In 20→100→60, clearing 100 leaves 60 applicable. Reversal alone does not discard a pending instruction, while a later verified whole-train instruction can supersede older directional state. The manager tests use the production begin/end/state functions and actual SavedData, rather than a separate rules simulator.

Final re-review outcome: **no remaining confirmed release-blocking functional finding in reviewed scope**, subject to the other review, final build/checks and local runtime acceptance. The second review subsequently identified coincident and delayed-coincident ordering; those were corrected, tested and re-reviewed there, and checked by the primary author.

Compatibility limitation retained: unusual old curves whose saved start chord cannot establish local direction can remain conservative without a usable anchor. Ordinary curved legacy migration is included in the final checks. Exact geometry recovery does not establish arbitrary topology equivalence.

The reviewer inspected the then-current 60-assertion check result; later focused cases and the final all-suite build were executed by the primary author and are recorded in [the correction report](ALPHA_1B_BOUNDARY_LIFECYCLE_CORRECTION.md). Live mixin application and in-game acceptance remain unverified.
