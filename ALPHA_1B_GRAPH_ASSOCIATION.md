# Alpha 1b - shared speed-board graph association

Base: `e179a9a0c184a70ca26242c454d2f1a651ea26d6` (cosmetic-X correction).
Branch: `feature/1.5.0-alpha-1b-visuals`. Internal version: `1.5.0-alpha.1b`.

## Evidence and root cause

The supplied observations for sign `73, 64, -174` were linked-track positions
`71.50, 63.50, -170.50`, `74.64, 63.00, -170.45`, and `77.53, 63.00, -168.49`.
The originating in-game logs and saved-world NBT were not present in the workspace.
The exact historical cause of all three values therefore remains unproven.

Verified from the logging call: "linked track point" prints the board's resolved
target, not the moving train. TrackLinkedBlockEntity.getTrackTargetPosition reads
the saved block/curve/segment. A valid curve uses a fraction derived from current
curve length; if that curve lookup fails, the old method falls back to the target
block centre. The first supplied value has the exact form of such a block centre.
That makes fallback a plausible explanation, not proof of that specific event.
Relinking or reconstruction/changed curve geometry could also change a valid
resolved target. New logs identify raw anchors and curve segment to distinguish
these cases during reproduction.

The enforceable defect is independent of that uncertainty: front passage used
only a 2.75-block world-space radius and facing test. The cosmetic-X correction's
edge-match guard protected the advance scout, not front enforcement. Rear release
was also a spatial sweep. The visual index separately projected a board and
returned the first matching edge. These mechanisms could disagree near close
parallel/crossover geometry. Reducing the radius would not fix graph identity.

Before implementation, the shared-association architecture and scope were reported
in chat. This is a larger refactor of association, not a new braking formula.

## Architecture and behaviour

- SpeedBoardGraphResolver derives an oriented association from the existing saved
  selected track block, curve endpoint/segment, track axis and sign facing. It
  records the actual Create TrackGraph (UUID), exact node pair/TrackEdge and edge
  position. It does not select a nearest edge or depend on X or running side.
- Curve identity uses the explicit saved endpoints and current loaded connection,
  validating graph geometry against it. Segment position follows installed Create
  6.0.10 TrackGraphHelper's `(segment + 1) / 2` convention. Missing curves no longer
  fall back to a block centre for speed-board authority.
- Straight bindings use the explicit selected block and single track axis, with
  numerical coincidence checks. Tilted transition blocks use Create's exact axis
  ends and `track.getYOffsetAt` to preserve yOffsetPixels. No neighbouring track
  walks or forced chunk loads are introduced. Multiple axes/graphs/edges with no
  unique binding are rejected rather than attached arbitrarily.
- A per-level, per-tick graph index keys straight lines and exact curve endpoints;
  boards query the relevant candidates, and each board resolves once per tick.
  This avoids repeated board-by-entire-network scans and dimension cache thrash.
  Numeric line keys are an index only: exact candidate validation still follows.
- SpeedBoardGraphLocation compares train samples on the same live graph and
  oriented edge. Front passage requires crossing the bound position in the valid
  travel direction. Close parallel, straight-versus-diverging and opposite-travel
  samples are not passages. Multiple crossed boards are applied in path order.
- Samples crossing an edge boundary use compatible graph continuations. Production
  inference is movement-bounded (derived from train speed, or the scout step),
  globally bounded to 128 visits/eight intermediate edges, and requires a unique
  plausible path. Rebuilt/stale graph samples, unknown paths and impossible jumps
  do not manufacture a crossing. There is no fixed two-pixel pickup radius.
- Advance braking uses the copied Create navigation path and graph positions,
  including board crossings inside a scout step just beyond a split. It chooses
  the first board by graph distance, not registry order or world proximity.
  Manual prediction now passes world UP to Create's steer selector, matching
  Navigation.control/controlSignalScout; the previous code passed motion as UP.
  The selector still consumes only a private navigation-path copy.
- Rear clearance uses the same association. Legacy frozen block-centre/curve
  coordinates are migrated only when matching the board's verified legacy target.
  Relinked/unknown boundaries are not released on an unrelated track.
- SpeedZoneBoardData now projects from that same resolver. Persisted visual
  projections are revalidated before reuse; unresolved/unloaded sign anchors are
  omitted rather than painted from stale geometry. The client matches exact node
  dimension/coordinates, preserving smoothed offsets and rejecting duplicate
  graphs. No visual packet format change is needed.

Cosmetic X, GUI, sign settings/category selection, sign textures/models, braking
envelope/formula, train physics, signal/shunt logic, LEFT/RIGHT configuration and
pulse/sleeper geometry are preserved. SpeedBoardRouteMatch remains for earlier
regression compatibility but no longer supplies operational association authority.

## Saved worlds and compatibility

No sign configuration, target-anchor, per-train SavedData or packet schema change.
Valid saved anchors are rebound to current graph objects automatically; boards
need not be replaced. A graph UUID/edge object is intentionally not treated as
permanent saved authority across graph splits/rebuilds. Active train restrictions
are retained, and pending higher/unrestricted limits use existing saved fields.

Missing, ambiguous or unloaded links are not enforced or highlighted as new
instructions. Existing active limits are retained. A pending increase without a
verifiable loaded board can remain restrictive until a valid subsequent
instruction/re-crossing; the code does not invent a safe release. Invalid old
links may need the existing relinking tool, not board replacement. In particular,
crossing blocks whose old data did not record an axis are deliberately ambiguous.

Both client and server should update for the complete correction: server graph
enforcement/association and client smoothed-node lookup changed. Networking remains
wire-compatible with the previous cosmetic-X build, but mixed versions do not
provide all fixes. Internal version stays 1.5.0-alpha.1b. Previous test JARs remain
separate. No merge into main or automatic issue closure.

## Focused diagnostics

In `config/create_signal_extensions-common.toml`:

```toml
[speed_restrictions]
graph_diagnostics = true
```

Default is false. New `[CSA SpeedGraph]` output identifies train UUID, board block,
raw selected block/curve/segment/direction and speed, associated graph UUID/oriented
node pair/edge position, observed train graph sample, FRONT/ADVANCE decisions and
JUNCTION options plus selected navigation/manual continuation. Unresolved reasons
include TRACK_UNLOADED, TRACK_REMOVED, CURVE_MISSING, AMBIGUOUS_TRACK_AXIS,
AMBIGUOUS_GRAPH, AMBIGUOUS_EDGE and NO_UNAMBIGUOUS_EDGE. Rejections also identify
uncrossed/wrong-edge/direction/position evaluations.

Limits: at most ten new messages per 20 server ticks globally, one per
train/board/phase/reason per 100 ticks, and at most 2048 remembered keys. This is
sampling: not every evaluation is printed. Disable after capture. The older
`speed_diagnostics` setting is independent and can also be set false to suppress
its existing persistence/operational messages. No new output is enabled by default.

## Validation and limitations

Final clean build uses Java 21 / Minecraft 1.21.1 / NeoForge 21.1.243 /
Create 6.0.10-280: all 862 assertions passed (the existing 806 plus 56 new graph
checks). All 14 Gradle tasks executed without build cache. Existing deprecation
and terminal-capability warnings remain; no compile errors.

The new checks use real Create TrackGraph/TrackEdge/TravellingPoint objects with
in-memory adjacency maps: single straight, close parallel tracks, multiple nearby
boards, main/diverging branches, crossover intersections, forward/reverse graph
samples and actual copied navigation, immediate post-split crossings, advance
envelope, saved anchors/X settings, graph removal/replacement and stale samples.
They also cover smoothed node offsets/exact endpoint association, legacy rear
target normalisation, manual UP steering and diagnostics limits/default. Existing
cosmetic-X parity, braking, running-side/TOML, palette and pulse suites still pass.

Two independent read-only review agents assessed operational and data/visual
scope separately. Their findings were fixed and re-reviewed; reports are in
`ALPHA_1B_GRAPH_ASSOCIATION_REVIEW_1.md` and `ALPHA_1B_GRAPH_ASSOCIATION_REVIEW_2.md`.

No live reproduction of the supplied world, in-game appearance, multiplayer
rollout or large-network TPS benchmark was performed. The tilted test verifies
the exact endpoint-binding function and client lookup, not a running world BE's
tilt-detection lifecycle. Live rebuilds may invalidate movement history for a
tick; unknown/over-budget paths are not guessed. Loaded anchor availability and
static overlay render/walk/material limits remain. Static highlighting depicts
known possible continuations, not a train-selected switch state.

Acceptance checklist: reproduce Station A's left-hand crossover with graph logs;
capture the `73,64,-174` anchor/curve fields and binding alongside each train UUID;
drive main and crossover routes in both directions; compare a nearby parallel
line and multiple signs; verify pre-braking and whole-train speed increases;
reload old boards, remove/rebuild track, relink invalid anchors, and test smoothed
curves/slopes, pulses and both running-side modes on a dedicated server.

## Files changed

Shared location/resolver/diagnostics helpers; SpeedRestrictionManager;
SpeedZoneBoardData/SpeedZoneSync; client SpeedZoneRenderer node lookup;
CommonConfig graph-diagnostic option; GraphAssociationChecks; Gradle test task
and corrective classifier; investigation and independent-review reports.

## Corrective test package

JAR: `build/libs/create_signalling_additions-1.5.0-alpha.1b-graph-association-test.jar`

Delivery: `dist/alpha-1b-graph-association/` includes that JAR, these notes, the
two independent review reports and SHA256SUMS.txt. Older deliveries are preserved.

SHA-256: `b52dce2a0d80b2e3f38fb47e7368f26f674abf3b6e0f39c4b735c98e306a378b`

Package inspection confirmed internal version 1.5.0-alpha.1b and exclusion of
regression classes. Compared to the cosmetic-X JAR, 185 existing mod classes are
identical, including GUI, sign settings/rendering, networking, signalling,
braking formula, running-side configuration and sleeper/pulse geometry. Changes
are confined to the documented graph-association paths and diagnostic setting.
Generated JARs, build outputs and logs are excluded from the source commit.
