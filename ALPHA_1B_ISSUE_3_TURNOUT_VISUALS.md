# Alpha 1b Issue #3 - turnout speed-board visual correction

Branch: `feature/1.5.0-alpha-1b-visuals`. Internal version: `1.5.0-alpha.1b`.

## Root cause and investigation

The original visual index (`SpeedZoneBoardData.update`) explicitly removed X/turnout
boards. Their default-category speed already existed on SpeedBoardBlockEntity,
but the visual snapshot/packet had neither those boards nor a turnout flag.
SpeedZoneRenderer consequently propagated each ordinary board's one speed to
every compatible continuation: an ordinary 100 km/h zone stayed green on both
routes despite a linked X20 instruction.

Inspected the existing visual index, sync, packet, sleeper renderer, graph walk,
and read-only X20 enforcement. Also inspected the installed Create 6.0.10-280
sources JAR: TravellingPoint.steer(NONE) minimises the absolute lateral dot
product of incoming and outgoing trajectories. TrackEdge.getDirection samples
the first/last quarter of an edge; using only a curve's initial tangent would
not distinguish tangent-connected turnout exits. An edge being a Bezier curve
does not by itself mean it is the diverging route.

CSA has sufficient speed values: an ordinary board supplies the normal limit,
and a turnout-mode board supplies its diverging limit. The missing data is the
visual turnout instruction, not a new operational speed calculation. There is
no reliable universal selected-switch state in the synced graph: actual route
choice is per train (navigation/steering). The correction depicts possibilities.

## Changes

- Include turnout boards in the visual index and persist their `Turnout` flag.
  Existing visual entries default to false when that optional NBT field is absent.
  Unrestricted boards remain ordinary boundaries. Operational train SavedData
  and the board's own configuration/persistence are unchanged.
- Transmit the flag with the visual board packet. Only that packet is registered
  with protocol version 2, on both client and server paths. Other packet versions
  and codecs are unchanged. Both client and server need this corrective build;
  the older Alpha 1b visual protocol is deliberately incompatible, despite the
  unchanged internal mod version.
- Preserve normal zone traversal and add a branch-only X overlay. X boards are
  not ordinary speed boundaries. Follow an X board's oriented graph link through
  unbranched approach track without painting it; at the first facing junction,
  classify the unique natural straight continuation and paint only other exits.
  Continue that restriction until the next same-direction ordinary/unrestricted
  boundary. At the next edge, a boundary at fraction zero also terminates it.
- Classification uses oriented Create edge trajectories and the junction normal,
  independent of compass direction, graph IDs, camera direction or curve flags.
  Equal or degenerate geometry returns unknown rather than guessing by map order.
- Existing per-face lower-speed ownership combines overlays: normal 100 remains
  green on the main route and X20 wins in blue on the diverging route. Existing
  opposite-direction halves and pulse distances are retained. Inspecting a normal
  zone includes its branch-specific face ownership instead of hiding the X tint.
- `SpeedZoneSleeperMesh`, original UVs, rail exclusion, depth-tested rendering,
  palette and pulse helper are unchanged. No enforcement, signal, train physics
  or route-selection code was modified. All graph access here is read-only.
- Corrective JARs use classifier `issue3-turnout-fix-test`; the original delivery
  in `dist/alpha-1b` is preserved. No merge into main or automatic issue closure.

## Validation and review

The new `turnoutVisualChecks` task runs as part of `check`, alongside the existing
Alpha 1a and Alpha 1b checks. Tests exercise the production classifier and packet
codec: eight orientations and both lateral mirrors, reversed approaches and map
ordering, slopes/tilted normals, curved approaches, symmetric Y and degenerate
geometry, facing versus trailing junctions, waiting/active X behaviour, green/blue
palette values, pulse phase, fixed/turnout packet round-trip and invalid counts.

Separate self-review reports are in `ALPHA_1B_ISSUE_3_REVIEW_1.md` and
`ALPHA_1B_ISSUE_3_REVIEW_2.md`. These are two review passes by the implementing
agent, not independent reviewers or in-game acceptance.

Final validation after the review improvement:

- `clean build --offline --no-build-cache --console=plain --no-watch-fs` passed
  on Java 21 / Minecraft 1.21.1 / NeoForge 21.1.243 / Create 6.0.10-280.
  All 11 Gradle tasks executed. All 25 Alpha 1a checks, 221 Alpha 1b assertions
  and 55 Issue #3 assertions passed (301 total). No compilation errors remain.
- Existing NeoForge deprecation warnings remain; the codec-only test also emits
  a harmless terminal-capability warning. No in-game success is inferred.
- Packaged metadata confirms internal version 1.5.0-alpha.1b; production helper,
  sleeper mesh and mixin entries are present, and regression test classes are
  excluded. Final class comparison again found 183 existing mod classes identical,
  with all changes confined to the documented visual classes.
- JAR: `build/libs/create_signalling_additions-1.5.0-alpha.1b-issue3-turnout-fix-test.jar`
- Delivery: `dist/alpha-1b-issue3-turnout-fix/` contains that JAR, review reports,
  these notes and SHA256SUMS.txt. Internal version is unchanged; filename is
  intentionally different from the original Alpha 1b build.
- SHA-256: `e19354c7df28d9d21712c1673c6274c953192ff9a6179d5a77e543748b2335c3`
- Original `dist/alpha-1b` JAR still has SHA-256
  `573a9da3678550bd9e57310e0029b5558074e6dffce848865be5e86da5084272`.
- Added `logs/` to .gitignore because the codec test creates compressed rolled
  logs; generated logs, build outputs and test JARs are excluded from the commit.

## Remaining limitations and in-game acceptance

- No interactive railway test was performed in this session. In-game appearance,
  route geometry and FPS still require acceptance testing; keep Issue #3 open.
- A normal colour requires a known upstream ordinary speed board. An X20 alone
  cannot invent a normal 100 km/h limit. Display uses the default train category,
  as before; MU/high-speed panel variants are not separate visual zones.
- Ambiguous equal-angle Y splits are left at the known normal tint, not assigned
  an arbitrary main branch. Trailing merges with only one compatible exit do not
  consume an armed X board. This represents a facing turnout instruction, not a
  selected route or an immediate restriction on every curved edge.
- Several possible paths can reconverge with different limits; shared faces keep
  the lower known restriction. Nested X boards likewise combine conservatively.
  Exact future per-train route history is not represented by static overlays.
- Existing render/walk budgets, loaded-chunk limits, whole-sleeper boundary
  resolution, standard-material assumptions and approximate Bezier pulse speed
  remain. Previously unindexed unloaded X boards appear when they are loaded and
  refreshed. The unrelated running-side request in Issue #2 is not implemented.

In-game checklist: place an ordinary 100 board followed by an X20 on the same
approach. Verify green main and blue diverging sleepers with show-all enabled and
by inspecting the approach; compare both ends, mirrored/rotated turnouts, curved
and ascending approaches. Check subsequent ordinary/unrestricted boards, a board
at the start of an outgoing edge, multiple X boards, a trailing merge and a
symmetric Y. Confirm pulses travel in restriction direction, rails and occlusion
are unchanged, and trains still obey the existing X20 and signal behaviour.
