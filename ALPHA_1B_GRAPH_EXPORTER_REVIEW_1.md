# Graph exporter — independent review 1

Date: 27 October 2026 (Australia/Sydney).
Scope: graph fidelity, topology, coordinates, geometry and diagnostic correctness.

A separate read-only reviewer inspected installed Create 6.0.10 source, exporter implementation, focused tests/results and a generated PNG. The reviewer did not modify files or run builds. Findings and re-review are recorded here by the primary author.

Confirmed findings corrected:

- Near-aligned midpoint coincidence was initially looser than CSA association. The exporter now requires CSA's quantized line identity before candidate projection.
- UUID-hash colours could collide. The sorted captured graph palette now assigns distinct colours.
- Nonfinite raw curve/link values could prevent matching JSON for other graphs. Capture now rejects invalid geometry or retains an invalid board as explicitly unresolved.
- Cardinal sign-facing arrows could misrepresent diagonal/curved travel. Arrows now follow the verified local graph tangent and orientation.
- Null registered graph entries initially failed before the per-graph guard. They now fail independently.
- Curve verification now checks the explicit saved anchor/endpoints as well as captured connection geometry.

Verified positively:

- Exact node positions include smoothing offsets; curves use captured authoritative published controls.
- Cold curve runtime caches remain untouched. Missing geometry is explicitly unavailable, not inferred.
- Portal links remain JSON-only. Projected crossings add no topology or junctions.
- Uniform X/Z projection has east right/south down, with full Y retained and overlap limitations explained.
- Physical board positions and verified graph positions have distinct symbols.
- Worker DTOs contain no live Create/world objects.

The reviewed focused log passed **52 assertions**, including actual curved PNG/non-chord pixels and CSA curve association parity. A later deterministic repeated-JSON assertion and final full-suite run are the primary author's validation, recorded in [the report](ALPHA_1B_GRAPH_EXPORTER_REPORT.md).

Final outcome: **no remaining confirmed release-blocking fidelity finding**, subject to final build and local runtime checks. An optional small-layout zoom improvement identified during visual review was applied by the primary author. Crowded elevation labels and the eight-entry image graph legend remain documented limitations. This review is not in-game acceptance.
