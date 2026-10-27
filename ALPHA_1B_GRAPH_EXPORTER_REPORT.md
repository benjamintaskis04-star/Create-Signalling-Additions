# Alpha 1b track-graph diagnostic exporter

Date: 27 October 2026 (Australia/Sydney).
Branch: `feature/1.5.0-alpha-1b-visuals`.
HEAD remains `6eb32911d525660df34241992c28ca3f22d1ce9f`; the previously completed boundary-lifecycle correction remains uncommitted and is included in this build.
Internal mod version: **1.5.0-alpha.1b**.

This is the requested final planned Alpha 1b diagnostic addition. It is not Alpha 2 or the Alpha 7 signal-map integration. No commit, push, merge, branch rename/deletion or roadmap change was performed.

## Usage and output

An operator with permission level 2, or the server console, can run:

```text
/csa graph export
```

Only one export can run at a time. The command captures registered graph data on the server thread, then writes images and files asynchronously. It reports the output directory when finished; failures are returned to the command source. It never runs automatically on a server tick.

Exports are inside the active world's root:

```text
<world-root>/csa-graph-exports/<UTC-timestamp>[-collision-suffix]/
    SUMMARY.txt
    dim-minecraft%3Aoverworld/
        graph.json
        overview.png
        tile_x0_z0.png
        ...
    dim-minecraft%3Athe_nether/
        graph.json
        overview.png
        ...
```

The timestamp uses UTC explicitly, for example `20261027T091907972Z`. A new directory is reserved with create-new semantics; repeated exports never overwrite earlier files. Dimension IDs are reversibly percent-encoded with a `dim-` prefix, preserving distinctions such as `a:b/c` versus `a:b_c` and preventing path traversal.

`SUMMARY.txt` describes completeness, omissions, limits and failures. Always read it before treating a diagram as a complete network. A failed graph is reported independently; other safely captured graphs remain available. Image/JSON output failures are isolated by dimension, with partial files retained for investigation rather than destructively cleaned up.

## Authoritative API investigation

The installed **Create 6.0.10-280 source JAR** was inspected directly:

`C:/Users/damat/.gradle/caches/modules-2/files-2.1/com.simibubi.create/create-1.21.1/6.0.10-280/23e1219501c0debfa0bb56c30ef8e0193341aae5/create-1.21.1-6.0.10-280-sources.jar`

| Source within that JAR | Finding used |
|---|---|
| `GlobalRailwayManager.java`, `trackNetworks` | Registered graphs are enumerated from `Create.RAILWAYS.trackNetworks`, not discovered by scanning or loading world chunks. |
| `graph/TrackGraph.java:47–59,120–129,374–384` | Graph UUID, node locations and adjacency maps define actual topology. `getNodes` is a key view; `locateNode` and `getConnectionsFrom` are read-only lookups. Nodes are not every physical track block. |
| `graph/TrackGraph.java:65–74` | Constructing even an unregistered TrackGraph increments Create's graph-ID counter. The production exporter does not create detached TrackGraph objects. |
| `graph/TrackNodeLocation.java:23–54` | Dimensions and exact positions include `yOffsetPixels`; exported node coordinates come directly from `getLocation`. |
| `graph/TrackEdge.java:81–103,118–131` | Curves use normalized geometry parameter `t`; straight smoothing can use adjacent curve starts. Interdimensional links have zero physical length and must not be drawn as world-space rail. |
| `track/BezierConnection.java:181–215,238–246,333–451` | A cubic uses Create's computed `finish1`/`finish2` controls. Calling cold geometry queries can initialize `lazyRuntime` and allocate a length-sized lookup table. |
| `graph/TrackGraphHelper.java:148–185` | Selected curve graph position uses `(segment + 1) / 2` in Create edge-position units. It is not a physical block-node index. |

CSA's current [SpeedBoardGraphResolver.java](src/main/java/com/benjamintaskis/createsignalextensions/content/speed/SpeedBoardGraphResolver.java) and [SpeedBoardGraphLocation.java](src/main/java/com/benjamintaskis/createsignalextensions/content/speed/SpeedBoardGraphLocation.java) were inspected for explicit anchor/curve identity, quantized straight-line indexing, outgoing-edge preference, smoothing offsets and sign-facing direction. The exporter mirrors those diagnostic association criteria on immutable values; it does not invoke or populate the operational resolver cache.

NeoForge 21.1.243's `RegisterCommandsEvent` source was also inspected. Registration uses the game event bus and Brigadier, with permission checks at the root, export leaf and handler to withstand command-tree merging.

## Implementation and changed files

| File | Responsibility |
|---|---|
| [GraphExportSnapshot.java](src/main/java/com/benjamintaskis/createsignalextensions/diagnostic/GraphExportSnapshot.java) | Bounded server-thread enumeration and immutable primitive/String/list DTOs; exact topology, smoothing, portal metadata, published curve controls, deterministic graph colours and independent graph failure reporting. |
| [GraphExportBoards.java](src/main/java/com/benjamintaskis/createsignalextensions/diagnostic/GraphExportBoards.java) | Loaded-board/explicit-link capture with loaded-chunk guards; worker-side diagnostic verification against frozen topology. Invalid links remain unresolved markers. |
| [GraphExportFiles.java](src/main/java/com/benjamintaskis/createsignalextensions/diagnostic/GraphExportFiles.java) | Uniform projection, bounded samples, headless Java2D PNGs, detail tiles, streaming JSON, unique safe output paths and summary. No Minecraft/Create access. |
| [GraphExportCommand.java](src/main/java/com/benjamintaskis/createsignalextensions/diagnostic/GraphExportCommand.java) | Operator-only command, one-job gate, bounded worker executor, shutdown cancellation and server-thread command feedback. |
| [CreateSignallingAdditions.java](src/main/java/com/benjamintaskis/createsignalextensions/CreateSignallingAdditions.java) | Two additional event listeners: command registration and exporter cancellation on stop. Existing operational listeners remain unchanged. |
| [SpeedRestrictionManager.java](src/main/java/com/benjamintaskis/createsignalextensions/content/speed/SpeedRestrictionManager.java) | Adds only `trackLinkedSnapshot(ServerLevel,int)`, a bounded read-only registry-view overload. Existing operational methods are unchanged. |
| [GraphExporterChecks.java](src/test/java/com/benjamintaskis/createsignalextensions/diagnostic/GraphExporterChecks.java) | Focused graph/geometry/read-only/permissions/file tests. |
| [build.gradle](build.gradle) | Adds `graphExporterChecks` to `check`; classifier becomes `graph-exporter-test`. |

No resource bundle/mixin changes were required for this addition. The existing boundary mixin remains enabled. Models, textures, UVs, client rendering/configuration, networking, speed categories, cosmetic X, shunt controls, signalling, Route Interface, train movement, braking and operational SavedData are unchanged.

Preservation was checked against the prior complete boundary-lifecycle source ZIP. The manager matches that source exactly after removing the new diagnostic-only overload. Boundary SavedData, anchors, mixin, tests, resource bundle, three original audit documents and three boundary correction/review reports are byte-identical. The previous diagnostic JAR remains at its distinct delivery path with SHA-256 `d72d4758279dd83d55facc3a6fe5820a1f54f40f93e83663f8c36416cba7caa5`.

## Read-only geometry and associations

Live Create structures are queried only during command capture on the server thread. Background work receives no graph, block entity, world, NBT or train objects.

The exporter reads an **already-published** Bezier runtime using version-specific reflection, copying its authoritative cubic controls and cached length. It does not call cold `getLength`/`getPosition`, generate baked sleepers/girders or construct substitute graphs. A cold, inaccessible or invalid runtime is explicitly marked unavailable; raw curve anchors/starts/axes remain in JSON when valid. This avoids mutating even Create's lazy geometry cache or triggering its potentially large cold lookup-table allocation.

Straight edges use exact node positions. Where smoothing offsets apply, adjacent curve-start coordinates are copied according to Create's `getPositionSmoothed` selection. Raster curves are drawn from captured cubic controls; intermediate JSON points use the same cubic. No rail is inferred between separate topology endpoints or across missing geometry.

Loaded speed boards retain physical block-centre coordinates, default numeric speed, unrestricted/cosmetic-X flags, facing, saved target direction and explicit target facts. Verification uses the saved track block/curve endpoint/segment or exact tilted endpoints. Straight candidates must match CSA's quantized line identity, not merely pass near the same midpoint. Curve candidates must match the saved anchor/endpoints and world-connection geometry. Direction comes from the same sign-facing test, and arrows follow the local graph tangent. Ambiguous candidates and incomplete graph topology remain unresolved. A board's X prefix never changes association logic.

The diagnostics describe current CSA mapping, including its `edgePosition / edgeLength` geometry projection. Normalized cubic `t` is labelled separately from Create edge-position units and is not claimed to be uniform arc distance. These diagnostics do not select a turnout route or alter how a train obeys the board.

## PNG legend and coordinates

- Uniform X/Z scale: **+X east/right; +Z south/down; north (-Z) up**.
- Coloured paths: authoritative registered edges; distinct colours are assigned in sorted captured-UUID order.
- Small circles: actual graph nodes. Larger black rings: actual nodes with more than two adjacent graph connections.
- Purple squares: physical loaded speed-board positions.
- Green diamonds/arrows: verified graph attachment and applicable local travel direction.
- Dotted green connectors: board associations, **not rails**.
- Orange X: unresolved loaded board; JSON gives the reason.
- Y labels and elevation-dependent dash styles indicate heights. Full XYZ coordinates are preserved in JSON. Labels can overlap; the legend explicitly warns that projected line crossings are not connections and that different elevations can overlap.
- Portal links and unavailable geometry are not drawn as rails.

The image footer shows the first eight graph ID prefixes/colours; all full IDs and colours are in JSON. The overview automatically fits captured bounds with a maximum 32 pixels/block, keeping small layouts readable. Every raster remains 1024×1024. Detail tiles are fixed 256-block squares at 2 pixels/block; for small graphs the overview can be more magnified than these coordinate-reference tiles.

## JSON schema, version 1

Each dimension's `graph.json` contains:

| Field | Contents |
|---|---|
| `schemaVersion`, `dimension`, `capturedTick` | Schema version, exact resource-location dimension ID and server tick captured. |
| `topologyComplete`, `registeredGraphCount`, `capturedGraphCount`, `captureErrors` | Explicit completeness and omissions. A partial graph set cannot verify attachment uniqueness. |
| `orientation`, `geometryParameter`, `limits` | Coordinate/unit descriptions and capture/output limits. |
| `graphs[].id`, `colour` | Full Create graph UUID and image colour. |
| `graphs[].nodes[]` | Graph-local node ID, Create network ID, dimension, full XYZ position, smoothing offset and actual adjacency degree. |
| `graphs[].edges[]` | Graph UUID plus canonical physical edge ID, oriented endpoint IDs/dimensions, reverse-connection presence, cached length or null, raw curve facts, and geometry. Each physical edge is exported once from Create's paired directed connections. |
| `edges[].edge.geometry` | `LINE`, `BEZIER` or `INTER_DIMENSION_LINK`; start/end and cubic controls when available; otherwise an explicit unavailable reason. Portal endpoint coordinates remain metadata, not a physical connecting line. |
| `edges[].samples`, `samplesLimited` | Bounded full-XYZ intermediate points and explicit sample-limit flag. Cubic controls remain exact even when sample lists are limited. |
| `speedBoards[].board` | Physical block-centre, readable block-coordinate ID, configured default speed/flags/facing and raw captured link facts. Default panel speed is diagnostic, not a claim of every train category's effective limit. |
| `speedBoards[].association` | `VERIFIED` or unresolved reason; verified graph/edge/from/to IDs, oriented edge position, normalized geometry parameter, orientation sign, attachment point and travel tangent. |
| `images[]` | Actual successfully written PNG names/types and the exact uniform projection transform, including bounds, pixel origin, pixels/block and image dimensions. |

For projection, `pixelX = originPixelX + (worldX - minX) * pixelsPerBlock`, and the analogous formula applies to Z/pixelY. Per-dimension files retain portal links touching that dimension, with both endpoint dimensions. Connectivity is expressed by IDs, never by raster intersection.

Output is ordered by graph UUID, node network ID, canonical edge ID, board dimension/ID and tile coordinates. JSON field ordering is explicit. Identical immutable snapshots produce byte-identical JSON in separate export directories. Capture tick/real graph changes naturally produce differences. Above the graph-count limit, the selected bounded registry prefix is explicitly partial; it is sorted before output, but selection itself follows the registry iterator.

## Safety and limits

| Limit | Behaviour |
|---|---|
| Capture deadline | Shared **50 ms soft deadline**, checked between bounded work units. Class loading/sorting/a single existing API call cannot be forcibly interrupted; no hard real-time latency guarantee is claimed. |
| Graphs/nodes/edges | At most 64 graphs, 8192 nodes and 16384 unique physical edges per command. An incomplete graph is discarded atomically and reported; other captured graphs remain. |
| Boards/dimensions scanned | At most 512 speed boards total and 64 loaded levels; each level's registered-sign view is bounded by the remaining board allowance plus one. An excess of auxiliary signs can truncate that view and is reported. Unloaded/unregistered boards are not discovered by chunk scanning. |
| Geometry coordinates | Finite values only; X/Z within approximately Minecraft's ±30-million-block bounds and bounded Y. Invalid raw/link values cannot poison another graph's JSON. |
| Samples | At most 257 intermediate samples per curve and 131072 globally. Image drawing retains captured cubic controls independently of sample truncation. |
| PNG/JSON output | At most 16 attempted dimensions and 32 PNG attempts, including failed writes; rasters are always 1024×1024 and are disposed one at a time. |
| Detail tiles | Up to four per dimension, subject to global budget; a bounded set of node/sample/midpoint/board tiles is selected deterministically. Omitted tiles are reported. Overview and JSON retain captured geometry; full raster coverage at detail scale is not promised for arbitrarily large networks. |
| Concurrency | One job globally; one bounded worker queue. No worker access to mutable Create/world state. Busy state is released on failure. Server-stop cancellation suppresses completion feedback and further dimension output. |
| Files | World-local hardcoded base, encoded dimension names, unique directories, create-new output files; I/O failures are reported without overwriting or deleting older exports. |
| Logging | Start/completion/failure command feedback; detailed per-graph/output failures go into summary/JSON, not per-tick gameplay logging. No new configuration setting. |

The exporter never calls restriction persistence/restoration/enforcement, reservations, navigation, signalling updates or graph mutators. It does not force-load chunks. Existing graph-index rebuild behaviour is untouched; broader optimisation remains Alpha 5 scope.

## Tests and build

The focused suite uses real Create graphs/edges/Bezier curves and checks:

- Multiple registered/disconnected graphs, canonical edges, empty graphs and multiple dimensions/portal links.
- Published curve controls against Create's actual geometry, intermediate Y, actual curved PNG pixels away from the endpoint chord, and CSA straight/curve association parity.
- Parallel/near-aligned rejection, invalid/unavailable associations, explicit curve anchor integrity and actual tangent direction.
- X/Z orientation, uniform projection, zero spans, huge coordinate bounds, samples and image/output limits.
- Different elevations crossing in projection without fabricated nodes/junctions.
- JSON/PNG dimensions/projection consistency, repeated deterministic JSON and non-overwriting paths.
- Invalid graph isolation, nonfinite geometry rejection, hard count/time limits, JSON-write failures across many dimensions, cancellation and file errors.
- Unchanged graph/node ID counters, original adjacency, live curve-cache identity and cold-cache state; immutable DTO independence after live graph mutation.
- Operator permission at root/leaf/handler, public-root merging, one-job gating and release after simulated worker failure.

Clean build uses Java **21.0.8**, Minecraft **1.21.1**, NeoForge **21.1.243**, Create **6.0.10-280** and Gradle **8.10**:

```powershell
$env:JAVA_HOME='C:/Program Files/Java/jdk-21'
./.gradle-bootstrap/gradle-8.10/bin/gradle.bat clean build --offline --no-build-cache --console=plain --no-watch-fs
```

**Actual final result: BUILD SUCCESSFUL in 39s; all 16 actionable tasks executed.** Production/test Java compilation, resource expansion, JAR assembly and every regression suite completed successfully. The previous **929** assertions and **53** exporter assertions all passed: **982 total**.

| Suite | Assertions | Result |
|---|---:|---|
| Alpha 1a | 25 | PASS |
| Alpha 1b visuals | 221 | PASS |
| Boundary lifecycle / manager / SavedData | 67 | PASS |
| Cosmetic X | 144 | PASS |
| Graph association | 56 | PASS |
| Graph exporter | 53 | PASS |
| Running side | 404 | PASS |
| Boundary/packet compatibility | 12 | PASS |

The final local log is `build-alpha1b-graph-exporter.log` (ignored build output). Existing API/Gradle deprecation and headless terminal-capability warnings remain; no compile errors or assertion failures. Tests are headless code/file checks, not live command or in-game acceptance. `BUILD.bat` was preserved and inspected; the clean Gradle command above was actually executed. The complete source delivery includes its required build resources.

## Independent reviews

[Review 1](ALPHA_1B_GRAPH_EXPORTER_REVIEW_1.md) separately reviewed graph fidelity, coordinates, geometry and diagnostic association correctness. [Review 2](ALPHA_1B_GRAPH_EXPORTER_REVIEW_2.md) reviewed thread safety, bounded work, files, permissions and compatibility. Both were read-only and found no remaining confirmed release-blocking issues after corrections. Neither reviewer claimed to execute builds or perform in-game testing.

Corrected findings include false near-aligned straight verification, colour collisions, invalid raw geometry poisoning JSON, cardinal-facing arrows, explicit curve-link identity, null-graph failure isolation, output limits under JSON-write failures and defensive permission checks. Focused tests cover these cases. Review 1 visually inspected a generated PNG; the primary author also inspected the output and improved small-layout overview zoom.

## Local-test readiness and remaining Alpha 1 acceptance

**Ready for local diagnostic testing: the final clean build and all 982 assertions passed. Alpha 1 is not declared complete or accepted.**

Remaining runtime checks:

1. In a backed-up local test world, run the command as an operator and verify an ordinary player cannot run it. Confirm command feedback and world-local output paths.
2. Export the actual Station A crossover, parallel tracks, curves/slopes and multiple elevations. Compare world coordinates, graph IDs and speed-board attachment markers/JSON; verify no false junction at projected crossings.
3. Check empty/newly built/cold geometry and temporarily unavailable board targets. They must be explicitly unresolved/unavailable without loading chunks or changing train speed.
4. Repeat exports before/after a genuine graph rebuild and while trains operate. Compare files, command latency and gameplay behaviour. Verify old exports remain intact.
5. Exercise large-network partial capture and filesystem failure on the actual Windows installation; dedicated-server headless/filesystem compatibility remains unverified and belongs to subsequent server testing.
6. Complete the boundary-lifecycle report's moving/stationary reload, long-train 20→100→60, reversal, unrestricted-boundary, route-specific braking and reconstruction acceptance checklist. The exporter does not substitute for those tests.
7. Check preserved NSW/shunt/Route Interface behaviour, cosmetic X, categories, LEFT-default/RIGHT highlighting and directional pulses in game. Existing automated checks passing is not runtime acceptance.

Known limitations: version-specific reflection can leave curves unavailable if Create's internal runtime access differs; cold curves are deliberately not initialized. Loaded-board-only scope, count/time budgets, partial detail coverage, eight-entry image graph legend and crowded elevation labels are explicit. No live command, full-world graph accuracy, server-tick latency benchmark or multiplayer acceptance is claimed.

## Distinct delivery

JAR: `dist/alpha-1b-graph-exporter/create_signalling_additions-1.5.0-alpha.1b-graph-exporter-test.jar`.

JAR SHA-256: `e75faa5a97cdd65bb327e81ab5f6dc90cb9c17e72096332833aae7a317e5bdb1`.

Complete Windows-buildable source: `dist/alpha-1b-graph-exporter/CSA_1.5.0_Alpha_1b_Graph_Exporter_Test_Source.zip`.

The folder also contains this report, the two reviews and `SHA256SUMS.txt`. Existing boundary-lifecycle and older diagnostic deliveries are preserved. Generated JARs/ZIPs/build output are not committed; all source/report changes remain uncommitted pending approval.
