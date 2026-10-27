# Alpha 1b graph exporter — slope-boundary elevation labels

Date: 27 October 2026. Presentation correction only; internal version **1.5.0-alpha.1b**.

## Baseline and scope

Branch remains `feature/1.5.0-alpha-1b-visuals`, HEAD `6eb32911d525660df34241992c28ca3f22d1ce9f`, with the existing uncommitted boundary-lifecycle corrections and graph exporter preserved. No commit, push or merge was performed. The original roadmap, three audit documents, boundary reports and original exporter reports were not rewritten.

The previous PNG renderer printed one Y label per available edge, positioned at its midpoint. Graph subdivisions therefore repeated elevations on level track and within continuous slopes. The correction changes only PNG annotations and their explanatory legend/summary text.

## Changed files

| File | Change |
|---|---|
| `src/main/java/com/benjamintaskis/createsignalextensions/diagnostic/GraphElevationLabels.java` | New worker-side geometry classification and bounded label placement from immutable snapshot values. |
| `src/main/java/com/benjamintaskis/createsignalextensions/diagnostic/GraphExportFiles.java` | Replaces midpoint text with slope-boundary annotations; updates legend/summary. Rail drawing, elevation-dependent strokes, JSON writing and sampling are unchanged. |
| `src/test/java/com/benjamintaskis/createsignalextensions/diagnostic/GraphElevationLabelChecks.java` | 43 focused geometric, layout and real PNG/JSON output assertions. |
| `build.gradle` | Runs `elevationLabelChecks` under `check`; uses distinct `graph-elevation-labels-test` JAR classifier. |
| This report and the two accompanying review records | Implementation, validation and independent review evidence. |

## Label rules

- **Level track:** no Y labels, whether straight or curved and regardless of node count. Flat overpasses retain their existing elevation styling and complete XYZ in JSON.
- **Straight slopes:** annotate their verified beginning/end. Compare gradients of connected edges using tangents directed out of the shared node; reversing graph-edge orientation cannot change the result. Equal continuous gradients do not gain intermediate labels.
- **Connected gradient changes:** one label at a verified shared endpoint for level/slope transitions, uphill/downhill reversals, or a gradient discontinuity exceeding **0.001 rise/run** (0.1 percentage point). Endpoint positions must coincide within **0.000001 block**. Node IDs alone do not establish a geometric join.
- **Bezier slopes:** use the exact start/end/control values already captured from Create. Endpoint slope activity detects eased gradients even where the endpoint tangent is horizontal. Analytically solve the quadratic `dy/dt = 0` for interior crests/troughs and evaluate the full cubic for their exact XYZ. A double root, where a continuous climb merely has a momentarily horizontal tangent, adds no label. Smoothly varying gradient within a cubic is one continuous slope; arbitrary sample points and maximum-gradient points are not labelled.
- **Junctions:** emit at most one annotation per shared graph node. At nodes with more than two incident edges, compare opposing horizontal tangent directions, avoiding false crests between two outgoing branches on the same side. The exporter does not select routes. Junctions with more than 16 incident edges are conservatively unlabelled, bounding pairwise comparisons.
- **Unavailable geometry:** do not infer curves, missing continuations or displaced smoothing joins from node positions or raw anchors. Unknown geometry, portal links, insufficient incident geometry and vertical/degenerate tangents suppress unverified endpoint labels. Independently verified interior extrema of an available curve remain eligible. A slope endpoint adjoining unavailable geometry may therefore remain unlabelled until authoritative geometry is available.
- **Identity:** deduplicate shared transition nodes within their actual graph; never merge independent graphs, projected intersections or different elevations merely because their X/Z coordinates overlap.

Existing authoritative geometry is supplied by `GraphExportSnapshot.Geometry`, copied using the Create 6.0.10 sources documented in `ALPHA_1B_GRAPH_EXPORTER_REPORT.md`: `TrackEdge.getPosition`/`getPositionSmoothed`, `BezierConnection.Runtime` cubic controls, and `TrackNodeLocation.getLocation`. This correction introduces no new capture, reflection or Create API access. It never assumes nodes represent every physical track block or every slope transition.

## Legibility and bounds

Labels use deterministic placement around exact projected anchors, white text backgrounds, a two-pixel collision margin and leader lines. All leaders draw first, then all label boxes/text, then the existing node and board/association markers. A later leader cannot strike through earlier text, and labels cannot obscure those markers.

Each image retains the existing **64-label** ceiling. Placement also stops after **512 visible candidate attempts**, with at most 32 candidate boxes per attempt; labels that cannot fit are omitted. Off-screen points do not consume that attempt budget. Geometry processing is bounded by the existing captured edge/node limits, and junction comparison is bounded separately. All work remains on the existing export worker; server-thread capture time and command scheduling are unchanged. No per-tick work, chunk loading, persistence writes or logging was added.

These are basic layout safeguards, not a guarantee of displaying every slope label in a dense overview. Closely spaced labels can be omitted or partially covered by a higher-priority node/board marker. Existing detail tiles and full JSON remain available. Missing or ambiguous geometry is deliberately not guessed.

## Validation actually executed

Java **21.0.8**, Minecraft **1.21.1**, NeoForge **21.1.243**, Create **6.0.10-280**, Gradle **8.10**:

```powershell
$env:JAVA_HOME='C:/Program Files/Java/jdk-21'
./.gradle-bootstrap/gradle-8.10/bin/gradle.bat clean build --offline --no-build-cache --console=plain --no-watch-fs
```

**BUILD SUCCESSFUL in 41s; all 17 actionable tasks executed. All 1,025 assertions passed.** Log: `build-alpha1b-graph-elevation-labels.log`.

| Suite | Assertions |
|---|---:|
| Alpha 1a | 25 |
| Alpha 1b visuals | 221 |
| Boundary lifecycle / manager / SavedData | 67 |
| Cosmetic X / actual routes | 144 |
| New elevation labels | 43 |
| Graph association | 56 |
| Original graph exporter | 53 |
| LEFT/RIGHT running side | 404 |
| Boundary/packet compatibility | 12 |
| **Total** | **1,025** |

The new checks cover flat lines/curves, straight and curved slopes, reversed edges and reversed cubic controls, connected and eased slopes, changed gradients, crests/troughs, double roots, coincident controls, shared junctions, same-grade branches, unavailable/portal/mismatched/degenerate geometry, incomplete incidence, flat and sloping overpasses, distinct graph identities, deterministic collision placement, count/attempt bounds, actual PNG text and marker layering, and exact JSON nodes/controls/samples/elevations/associations.

Early development runs caught test compilation typos and an incorrectly positioned small-map test fixture. These were corrected; the final clean build above passed every check. Existing API/Gradle deprecation and headless terminal-capability warnings remain; no final compile errors or failed assertions.

Supplementary preservation checks:

- All **four distinct original exporter JSON fixture outputs are byte-for-byte identical** to their pre-change output, covering the original multi-dimension, empty and curved export fixtures.
- The JSON writer's source text is unchanged. Only `GraphExportFiles.java` and `build.gradle` changed among the pre-existing files recorded in the baseline hash manifest.
- All **232 pre-existing compiled classes outside `GraphExportFiles` and its nested records are byte-identical** to the previous exporter JAR. This includes graph capture, associations, command handling and operational code.
- Internal packaged mod metadata still declares `1.5.0-alpha.1b`.
- The previous graph-exporter JAR retains SHA-256 `e75faa5a97cdd65bb327e81ab5f6dc90cb9c17e72096332833aae7a317e5bdb1`.

## Independent reviews

[Review 1](ALPHA_1B_GRAPH_ELEVATION_LABELS_REVIEW_1.md): geometric correctness, coordinates, continuity, curves and diagnostic fidelity. [Review 2](ALPHA_1B_GRAPH_ELEVATION_LABELS_REVIEW_2.md): presentation-only scope, bounded work, layering, compatibility and regressions. Both independently found **no remaining confirmed blocker** after corrections.

Their findings led to separate leader/text passes, preserving nodes/boards above annotations, the visible-candidate cap, and additional reversed-cubic, flat-overpass, marker-visibility and budget tests. Review 1 inspected the final generated PNG; both inspected the executed final build log. Neither claimed to run an in-game test or execute the build themselves.

## Delivery and acceptance

Distinct JAR:
`dist/alpha-1b-graph-elevation-labels/create_signalling_additions-1.5.0-alpha.1b-graph-elevation-labels-test.jar`

SHA-256: `834fe5e73dc105ef4a9ec18c4b737ee003f12b54f4b9ea2958d6d1a998443e0b`.

Complete Windows-buildable source:
`dist/alpha-1b-graph-elevation-labels/CSA_1.5.0_Alpha_1b_Graph_Elevation_Labels_Test_Source.zip`

The previous `dist/alpha-1b-graph-exporter` and boundary-lifecycle packages are preserved. Source/build configuration remains buildable with `BUILD.bat`. No generated binaries are staged or committed.

**Ready for local diagnostic testing.** Run the existing `/csa graph export` command against level track, eased slopes, long slopes subdivided by nodes, changing gradients, turnouts and overpasses. Confirm expected boundary labels and readable nearby transitions in both overview and detail images; compare JSON and board markers before/after. Cold geometry must remain unguessed.

This is a server-side export presentation change (or the integrated server in a local game). A dedicated server needs this build to generate the new PNG annotations; clients do not need an update for protocol compatibility. No networking, save format, speed enforcement, signalling, movement, braking, GUI, sleeper visuals, LEFT/RIGHT settings or pulses changed.

Alpha 1 is **not declared accepted**. Existing runtime acceptance requirements remain: moving/stationary reloads, rear clearance, long-train multiple boundaries, stops/reversals, route-specific braking at crossovers, graph reconstruction and preserved signalling/visual behaviour. Automated PNG tests do not replace those tests.
