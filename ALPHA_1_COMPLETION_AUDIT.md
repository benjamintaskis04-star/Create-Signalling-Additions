# CSA 1.5.0 — Alpha 1 completion audit

Audit date: 27 October 2026 (Australia/Sydney).

**Recommendation: NOT READY.** Most original Alpha 1 persistence requirements are implemented, including stationary restoration, validated saved records, change-only dirty marking and permanently deleted-train cleanup. However, graph crossing history is not initialized before the first movement after restoration, and pending rear-clearance handling can lose a crossing. A separate multiple-boundary sequence can raise the restriction above a section already occupied by the train. These are traced implementation defects, not conclusions drawn from missing tests or reports of observed in-game failures.

No production code, tests, configuration, assets, roadmap or Git history were changed during this audit. No commit, push, merge, branch deletion or Alpha 2 development was performed. This report and the two supporting review documents are intentionally uncommitted.

## Scope and starting state

- Authoritative specification: the complete [CSA_1.5.0_Roadmap.md](CSA_1.5.0_Roadmap.md), including the release table, agreed design principles, packaging conventions, immediate milestone and progress log.
- Audited branch: `feature/1.5.0-alpha-1b-visuals`.
- Audited commit: `6eb32911d525660df34241992c28ca3f22d1ce9f`, including the cosmetic-X and graph-association corrections.
- Before inspection, the working tree was clean. `git ls-remote origin refs/heads/feature/1.5.0-alpha-1b-visuals` returned exactly the local HEAD above. The remote is `https://github.com/benjamintaskis04-star/Create-Signalling-Additions`.
- Internal mod version remains `1.5.0-alpha.1b`.
- Roadmap SHA-256 before the audit: `76a76c10c1e20eefe859490d2cfe2cafea40cab6b93db78e93a2bfd424fa4c84`.

Alpha 1's milestone is persistent speed restrictions. Alpha 2 shunting improvements, Alpha 3 closest-train priority, Alpha 4 reservation handling, Alpha 5 configuration/performance overhaul, Alpha 6 Low Speed signalling, Alpha 7 railway maps and Beta multiplayer acceptance are not retroactively required for Alpha 1. The roadmap's cross-cutting safety, persistence, resource and packaging principles still apply. The Alpha 1a/1b additions are assessed separately below.

## Evidence and reference key

Line numbers refer to the audited commit, not a future correction.

| Key | Source |
|---|---|
| M | [SpeedRestrictionManager.java](src/main/java/com/benjamintaskis/createsignalextensions/content/speed/SpeedRestrictionManager.java) |
| D | [SpeedRestrictionSavedData.java](src/main/java/com/benjamintaskis/createsignalextensions/content/speed/SpeedRestrictionSavedData.java) |
| G | [SpeedBoardGraphLocation.java](src/main/java/com/benjamintaskis/createsignalextensions/content/speed/SpeedBoardGraphLocation.java) |
| R | [SpeedBoardGraphResolver.java](src/main/java/com/benjamintaskis/createsignalextensions/content/speed/SpeedBoardGraphResolver.java) |
| L | [TrackLinkedBlockEntity.java](src/main/java/com/benjamintaskis/createsignalextensions/content/speed/TrackLinkedBlockEntity.java) |
| V | [SpeedZoneBoardData.java](src/main/java/com/benjamintaskis/createsignalextensions/content/speed/SpeedZoneBoardData.java) |
| S | [SpeedZoneSync.java](src/main/java/com/benjamintaskis/createsignalextensions/content/speed/SpeedZoneSync.java) |
| N | [TrainSpeedMixin.java](src/main/java/com/benjamintaskis/createsignalextensions/mixin/TrainSpeedMixin.java) |
| T1 | [Alpha1aChecks.java](src/test/java/com/benjamintaskis/createsignalextensions/Alpha1aChecks.java) |
| TG | [GraphAssociationChecks.java](src/test/java/com/benjamintaskis/createsignalextensions/content/speed/GraphAssociationChecks.java) |

Additional dependency evidence was read from the installed **Create 6.0.10-280 source JAR**, in the local Gradle cache under `com.simibubi.create/create-1.21.1/6.0.10-280/23e1219501c0debfa0bb56c30ef8e0193341aae5/`. References below to Create source use paths within that JAR:

- `com/simibubi/create/foundation/events/CommonEvents.java:133–142`: Create's railway tick runs during `LevelTickEvent.Post`.
- `com/simibubi/create/content/trains/GlobalRailwayManager.java:82–98,121–127,193–258`: world-load registry initialization, permanent removal and train ticking.
- `com/simibubi/create/content/trains/entity/Train.java:289–324,1207–1227`: navigation precedes carriage movement; saved speed and target speed are restored.

**Status meaning:** Complete means the required mechanism is demonstrably connected in source, with its evidence limits stated. Partially complete means a demonstrated gap affects some required cases. Cannot verify means the available evidence cannot establish that claim, not that an implementation is absent. None of the complete source findings imply an unperformed in-game test passed. No original Alpha 1 subsystem was found wholly unimplemented.

## Requirement-by-requirement comparison

Roadmap wording is quoted or faithfully split into independently assessable obligations. Closely related repeated statements are grouped; their implementation is not counted twice.

| ID | Requirement from the roadmap | Implementation and evidence | Status | Remaining work |
|---|---|---|---|---|
| A1 | “Save and restore active restrictions exactly across world reloads” / “Finish and connect saved restriction … restoration.” | D:26–29,68–103,107–139 stores posted limit, legacy route limit, shunt authority, route serial and pending fields by train UUID in overworld SavedData. M:147–170 copies saved active values directly; it does not reconstruct the active cap from nearby boards. M:174–179 connects updates each eligible train tick. N:27–36 → M:763–783 restores before an automatic navigation command is consumed. No dedicated train-restriction NBT round-trip test exists. | **Complete** for saved active values. First subsequent board passage is separately incomplete under A3/A8. | Runtime round-trip acceptance remains; no missing active-value persistence mechanism found. |
| A2 | “… including stationary trains.” | M:123–138 has no speed/movement condition before `restoreState`, enforcement and persistence. A valid stationary train restores on its first eligible tick. Graphless trains retain saved data until usable again. T1's stationary check covers a helper's non-release, not actual SavedData restoration. | **Complete** by call-path inspection. | Stop/reload/resume acceptance with actual train UUID/cap evidence. |
| A3 | “Restore saved speed restrictions exactly on reload, while still allowing new, more restrictive instructions to apply.” | M:340–399 applies lower/equal instructions at the front and discards stale increases; active values restore exactly. However, M:233–235 requires front crossing history that `restoreState` does not initialize before movement. A lower board crossed on that first movement can be missed (F1). | **Partially complete** | Correct first-movement history and add lifecycle regressions. |
| A4 | “Preserve pending higher-speed board transitions until the entire train clears the boundary” / “… pending-boundary restoration.” | D:26–29,77–95,117–127 saves the ordered queue, speed/unrestricted value, sign, dimension, route serial, target and forward vector. M:160–167 restores/deduplicates it; M:359–381 queues increases, M:403–440 waits for the trailing carriage's graph crossing. Queue persistence is implemented. Release depends on samples and an available current association; lost first/rebuild crossings can strand it (F1). The multiple-boundary sequence also has F2. | **Partially complete** | Correct crossing initialization/recovery and occupied-section limit handling; retain existing saved fields where possible. |
| A5 | “Validate records.” | D:70–95 requires UUIDs, positive present limits, valid dimension identifiers, a stored sign position, bounded key length and at most 4096 accepted pending entries; D:142–146 rejects nonfinite optional vectors. M:160–167 suppresses duplicate restored boundary keys/dimensions. Optional old target/direction fields remain readable for migration. No dedicated malformed SavedData test exists. | **Complete** for implemented record validation. | Focused malformed/legacy record tests are useful validation, not evidence that validation is absent. |
| A6 | “Clean up permanently deleted trains” / “Handle train deletion and stale data safely.” | M:117–120 calls D:`retainTrains` every 200 ticks against **all** `Create.RAILWAYS.trains.keySet()`. D:38–39 removes only UUIDs absent from that registry and dirties only on removal. M:141–144 separately discards transient scan states without deleting valid saved records. Installed Create initializes the complete registry before ordinary ticks and removes disassembled/deleted trains from it. | **Complete** | In-game deletion/virtual-train acceptance test remains. **No deleted-train cleanup defect found.** |
| A7 | “Avoid needless writes” / “Audit persistence for unnecessary processing and disk writes.” | M:174–179 calls update each train tick; D:51–65 compares immutable Entry/Boundary content before dirty marking. Empty unrestricted records are removed only if present. D:38–46 similarly dirties only for real cleanup/removal. No explicit per-tick disk save is invoked. V:35–50 uses analogous change checks for visual data. | **Complete** for restriction dirty/write suppression; audit performed. | Per-tick object construction can be profiled separately; it is not a confirmed extra disk write. |
| A8 | “Review edge cases: stops, reversals, graph changes and world reloads.” | [TrainTravelDirection.java](src/main/java/com/benjamintaskis/createsignalextensions/content/speed/TrainTravelDirection.java):9–13 uses motion, navigation intent, then target speed. M:187–203 chooses leading/trailing carriage accordingly. G:60–75 rejects reversed/stale/ambiguous movement history. R rebinds saved board anchors to current graphs. Stops preserve active caps. F1 leaves reliable passage/release incomplete when history is missing or invalidated. TG covers geometric rejection/rebinding, not manager recovery. | **Partially complete** | First moving tick, queue creation, reversal/resume and rebuild-over-crossing lifecycle tests/corrections. |
| A9 | “Avoid … chunk loads” / “forced chunk loads.” | Restriction storage/cleanup reads registry/SavedData, not chunks. M:247,522 guards local world reads; R:50 checks target chunk before block/BE access; L:97 guards legacy curve access. Graph indexing/travel uses Create graph objects. Unknown links fail conservatively. | **Complete** in examined speed-state/association paths. | Loaded/unloaded-anchor runtime checks remain; no missing chunk guard identified here. |
| A10 | “Avoid unnecessary bloat, repeated whole-network scans, … excessive allocations …” | State/cache lifecycles are bounded/cleaned as described below. But R:37–45 rebuilds an index each queried level/game tick; R:73–94 walks network nodes/edges even when unchanged. M also scans boards and allocates snapshots per train. The repeated work is established; whether all of it is avoidable safely and its cost are unmeasured. | **Partially complete** against the cross-cutting scan principle; no demonstrated TPS defect. | Assess safe index invalidation/reuse and profile representative layouts. Do not import the entire Alpha 5 overhaul into Alpha 1. |
| A11 | “Server remains authoritative for operational rules” / “Use Create's signalling and reservation systems as the foundation rather than replacing them.” | M server event → Create registry/TravellingPoint; copied navigation path at M:489–496,611–650 avoids consuming real navigation decisions. N caps Create's own command. Client renderer/config does not supply operational limits. No replacement reservation system introduced by these Alpha 1 corrections. | **Complete** for the audited paths. | Normal signalling smoke checks remain; no new Alpha 4 requirement inferred. |
| A12 | “Each alpha includes the features from preceding alphas; avoid introducing later-stage features prematurely” / “Signal mapping is not in Alpha 1.” | Current Alpha 1b retains the restriction SavedData and baseline systems. Additional track highlighting is a local speed-zone inspection overlay, not Alpha 7 live-signal map integration. No Alpha 2 work was started in this audit. Older comments referring to Alpha 2/5/6 describe inherited code and are not evidence of approved later milestones being complete. | **Complete** for milestone scope separation. | F1/F2 still prevent claiming fully correct retained Alpha 1 boundary behaviour. |
| A13 | “Alpha builds are tested locally only. Server/multiplayer testing starts at beta.” | This audit performed a local Windows Java build/headless checks. No dedicated-server or multiplayer acceptance is claimed or required to pass Alpha 1. | **Complete** for validation scope. | Local runtime acceptance below. |

### Packaging and review requirements

| ID | Requirement from the roadmap | Implementation/evidence | Status | Remaining work |
|---|---|---|---|---|
| P1 | “Complete Windows-buildable source ZIP with BUILD.bat”; complete Java source, resources and build configuration. | Repository contains `src`, `build-support/static-resources.zip`, [build.gradle](build.gradle), [settings.gradle](settings.gradle), [gradle.properties](gradle.properties), and [BUILD.bat](BUILD.bat). BUILD bootstraps Gradle, copies a temporary local workspace, builds and returns failures. Actual Windows compilation was executed below. The complete historical delivery ZIP was not available for inspection in this checkout. | **Complete** for source/build components; **Cannot verify** historical archive completeness. | Inspect the delivered source ZIP if historical packaging acceptance is required. Do not create a new release package during this audit. |
| P2 | “Deliver CSA_1.5.0_Alpha_1.zip for a local Windows build attempt, not as a claimed precompiled JAR.” | [ALPHA_1_README.md](ALPHA_1_README.md) explicitly describes source/local build attempt. No archive with this exact name was available in the audited workspace. Later, explicitly requested Alpha 1b test JARs are a separate delivery convention. | **Cannot verify** the original delivery artifact. | Obtain/check the original archive or delivery record; absence here is not proof it was never delivered. |
| P3 | “Perform two reviews of each package before delivery; correct issues discovered.” | Historical review documents exist. Two new independent source reviews were performed for this audit with different emphases, recorded separately below. F1/F2 were not corrected because the user prohibited production changes. | **Complete** for the two audit reviews; **Partially complete** for readiness while findings remain open. | Correct/review only the agreed findings before acceptance or a new corrective delivery. |
| P4 | “State clearly whether compilation was actually verified. A ZIP integrity check is not a successful Java build.” Target Java 21 / Minecraft 1.21.1 / NeoForge 21.1.243 / Create 6.0.10. | Actual clean build succeeded using JDK 21.0.8 and the pinned platform versions, including Create 6.0.10-280. All regression tasks ran. Details below. | **Complete** | None for compilation. This does not establish in-game acceptance. |
| P5 | Initial failed alpha repair naming “Alpha N Fix”, followed by “Alpha N-1”, “Alpha N-2”, etc. | Historical build-attempt/repair archive sequence is not fully present. Current distinct `graph-association-test` artifact follows the user's later explicit corrective-test naming instruction and keeps internal `1.5.0-alpha.1b`. | **Cannot verify** historical naming sequence. Current corrective classifier is deliberate. | Check original deliveries only if needed; no rename performed or proposed here. |
| P6 | “Preserve original Blockbench-exported geometry, UVs, texture assignments, rotations and proportions unless an agreed change is necessary.” | `git diff 1123dce HEAD -- src/main/resources build-support/static-resources.zip` is empty: source assets and the actual build resource bundle are unchanged from the earliest tracked Alpha 1a source. Original external Blockbench exports/1.4.1 archive are unavailable for provenance comparison. | **Complete** for preservation across audited development history. | Cannot independently verify original export provenance from this checkout. |
| P7 | “Keep the CSA Signal Linking Tool out of … heightvariable item tag.” | [heightvariable.json](src/main/resources/data/create_signal_extensions/tags/item/heightvariable.json) omits `signal_linking_tool`. The actual `build-support/static-resources.zip` tag was separately opened and also omits it. | **Complete** | None. |

## Traced operational and persistence paths

1. Event registration in [CreateSignallingAdditions.java](src/main/java/com/benjamintaskis/createsignalextensions/CreateSignallingAdditions.java):51–54 connects manager server tick/stop and visual sync/login. These are live call paths, not unused persistence APIs.
2. M:`onServerTick` periodically prunes saved records, then restores each eligible registered train, scans front/rear, scouts its route, updates/caps its instruction and persists state. A zero-speed train is not excluded. A graphless train is excluded from motion evaluation but not from the authoritative saved-record retention set.
3. N's injection calls M:`applyNavigationTargetCap` before Create's automatic `approachTargetSpeed`. A missing state restores directly from the saved record before that cap is consumed. This closes the active-cap startup path; it does not initialize movement history.
4. L's saved block/curve/segment survives old-world loading. R resolves a unique live graph/node pair/edge/position oriented by sign facing; missing/ambiguous targets do not attach to a nearby edge. G compares actual train/scout graph samples, direction and bounded compatible paths.
5. M:`applySpeedBoard` selects the unchanged category/manual numeric speed; X is not an operational input. Lower/equal instructions apply at the leading end. Increases/unrestricted instructions enter the saved queue, and M:`scanRearForPendingBoard` applies the frozen limit after a verified trailing graph passage.
6. D:`update` structurally compares the next state, marking dirty only for a genuine change. Minecraft's SavedData lifecycle handles disk serialization. M:`onServerStopped` clears transient states, loaded sign registry, visual sync cache, resolver and graph diagnostics.

## Confirmed findings and smallest proposed corrections

### F1 — Missing/rejected observation history can permanently lose a board passage

**Evidence level: confirmed from source and Create tick ordering; not reproduced in-game.** This is the acceptance blocker affecting original Alpha 1 reload/pending-boundary requirements.

`restoreState` initializes active limits and pending records but leaves `lastFrontGraph` and `lastRearGraph` null (M:147–170,942–943). Create moves trains during its level-post tick, before CSA's server-post observation. Create also restores nonzero saved train speed (`Train.read:1223`); a stationary initialization tick cannot be assumed. G:`crossed` rejects null previous samples (G:60–69).

A concrete source-level trace on an ordinary forward edge, with the board at position 10:

| Observation | Front crossing a new lower board | Rear crossing a restored increase |
|---|---|---|
| Saved state | Posted 60; front at 9.9; ordinary 20 board at 10 | Posted 20; 60 boundary pending at 10; rear at 9.9 |
| First movement after reload | Front moves beyond 10 before CSA observes it | Rear moves beyond 10 before CSA observes it |
| First CSA observation | Previous front graph sample is null; passage rejected | Previous rear graph sample is null; release rejected |
| Following ticks | Both samples are beyond 10; no new crossing; scout sees the board behind | Both samples are beyond 10; no new crossing; pending 60 can remain stuck |

These traces assume a usable graph, normally loaded linked board and sufficient ordinary tick movement to cross the nearby boundary. They do not depend on X, turnout geometry, high speed or chunk loading. They were traced, not added as executable tests during this audit.

Additionally, M:405–407 returns before updating rear history whenever the pending queue is empty. The first new pending increase can therefore inherit missing/stale history. Sampling rear positions every valid tick addresses that normal queue-creation case, but does not by itself fix first movement after reload.

Rear release also now requires a registered board and a resolvable loaded anchor (M:413–431; R:48–58). If association availability or a rebuild invalidates the crossing tick, later recovery does not replay that crossing. The conservative result retains a lower cap rather than guessing a release. Removal/relink ambiguity should remain conservative; ordinary temporary loss still needs safe recovery. This is the documented graph-correction limitation, assessed here against Alpha 1's earlier lifecycle requirement.

Compared with earliest tracked source `1123dce`, the older radius-based rear helper could accept a later nearby segment already beyond the boundary; current graph crossing requires a real before/after pair. The old pickup window could mask an initialization gap. The proper correction is valid graph observation lifecycle/reconciliation, preserving the nearby-track fix; restoring broad proximity pickup is not proposed.

**Smallest proposed scope:** initialize current leading/trailing graph observations before the first movement after restore/reassociation; maintain valid rear history independently of queue occupancy; safely reconcile pending boundaries after temporary graph/board availability loss using verified route/direction information. Do not release merely because a world coordinate is beyond a sign, force chunks, change braking formulas or attach to a guessed branch. Add manager-level lifecycle regressions for these exact cases.

### F2 — Multiple pending increases can temporarily exceed an occupied section's limit

**Evidence level: deterministic source-level state trace, independently agreed by both reviewers; not reproduced in-game.** This concerns existing boundary enforcement, not failure to serialize the queue.

For a sufficiently long train, let posted speed be 20. Its front passes a 100 board and then a 60 board before the rear reaches either. Both instructions compare with current effective **20** (M:355–375), so the queue becomes `[100, 60]`. When the rear crosses the 100 boundary, M:432 sets posted to **100** even though the leading portion already occupies the 60 section. The 60 board is behind the front, so the forward scout does not restore that cap. Until rear clearance of 60, the stored cap can therefore exceed a restriction belonging to an occupied section.

This comparison/assignment pattern is also present in the earliest tracked source `1123dce`; it is not attributable to cosmetic X or the new graph identity resolver. The problem is meaningful even though each queue record survives reload correctly. This is not a claim that the user has observed overspeed.

**Smallest proposed scope:** retain the effective minimum required by all sections currently spanned when applying queued releases; do not let release of an earlier boundary override a later lower instruction already encountered by the front. Preserve queue order, exact reload behaviour and directional rear clearance. Test 20→100→60, monotonic increases, unrestricted transitions and both travel directions. Do not change train physics or the braking formula.

### Findings that are not confirmed functional defects

- No deleted-train cleanup bug was found. There is no need to add a deletion hook merely because a hook-specific API is unused; the periodic full-registry reconciliation already implements the requirement.
- There is no confirmed needless restriction SavedData disk write. Per-tick calls and temporary objects are distinct from dirty marking and disk I/O.
- Repeated graph/scout work is established below, but no TPS benchmark, memory leak or maximum-layout failure was demonstrated.
- Missing automated persistence tests do not prove missing persistence. The connected implementation is credited independently of coverage.
- Exact historical archive delivery/naming and original external asset provenance remain unavailable evidence, not demonstrated code defects.

## Performance, persistence and retained-state audit

| Area | Confirmed behaviour | Assessment |
|---|---|---|
| Restriction writes | Every eligible train calls `persistState`; D compares Entry content, does not save/dirty unchanged values, and does not retain empty unrestricted records. | No confirmed redundant restriction writes. Dirty state is not an immediate disk write. |
| Temporary persistence allocations | M:177–179 projects queue records/list each tick; D:58 constructs an Entry. | Confirmed repeated allocation; profiling/caching opportunity, not missing persistence. |
| Deleted-state retention | Saved prune every 200 ticks; transient state pruned against eligible scan; server stop clears static maps. | Correct bounded-lifetime restriction cleanup. Removal may wait up to the next 200-tick sweep; immediate deletion is not required. |
| Network index | R:37–45 replaces each queried level's index on a new game time; R:73–94 loops graphs/nodes/edges. Board results are reused within that level/tick. | Removes per-board whole-network rebuilding but still repeats a whole-network index scan on unchanged ticks. Cross-cutting roadmap principle gap; safe invalidation/reuse deserves assessment, without assuming a measured performance failure. |
| Per-train scans | M:123 scans the train registry once per manager tick; M:319 scans loaded boards; M:456 builds scout snapshots; M:548 checks snapshot boards each one-block step. | Train iteration is needed for enforcement. Board work can scale with train count × board count × scout steps; necessity and impact of all repeated work are not measured. |
| Scout horizon | [SpeedBrakingEnvelope.java](src/main/java/com/benjamintaskis/createsignalextensions/content/speed/SpeedBrakingEnvelope.java):6–8 bounds look-ahead to 640–8192 blocks; scan also runs when stopped. Navigation path copies/samples are transient. | Bounded but potentially expensive. Broader tuning belongs to Alpha 5; do not infer a present TPS bug. |
| Chunk access | Relevant manager/resolver/legacy target world reads have `hasChunkAt` guards; associations/scouting use Create graph objects. | No forced chunk load found in these paths. Missing anchors do not justify silently choosing another edge. |
| Visual writes/network | V updates/removes only changed projections; S runs every 20 ticks and compares per-player payload before sending. Unloaded/unvalidated projections are discarded. | No confirmed per-tick unchanged visual write; load/unload changes can legitimately dirty the visual index. |
| Graph diagnostics | [SpeedGraphDiagnostics.java](src/main/java/com/benjamintaskis/createsignalextensions/content/speed/SpeedGraphDiagnostics.java):15–28: default off, at most 10 messages/20 ticks, 100-tick per-key spacing, max 2048 remembered keys. | Bounded and controllable. |
| Legacy diagnostics | [CommonConfig.java](src/main/java/com/benjamintaskis/createsignalextensions/config/CommonConfig.java):39–40 defaults `speed_diagnostics` on. D logs records on load/save; M:787–793 samples NAV roughly once/32 ticks/train. | Controllable but no global cap; volume grows with train count. No claim that new graph diagnostics flood normal logs. |
| Visual/client bounds | S removes disconnected SENT entries every second. Renderer limits boards/walks/faces; graph traversal rejects stale authority. Resolver cache is per-level weak-keyed and replaced per tick; all these caches clear at stop. | No demonstrated unbounded leak. Live boundary queues lack a hard insertion cap, although duplicate keys are suppressed; load accepts at most 4096. No real layout overflow was demonstrated. |

Create's own `Train.tick:290` calls `markTracksDirty`; that dependency behaviour must not be misattributed to CSA's restriction SavedData.

Separate inherited category/scheduled-shunting stores are not this restriction store. Their different cleanup policies do not establish a failure of the original Alpha 1 restriction-record cleanup requirement. No later-alpha subsystem changes are proposed here.

## Alpha 1a/1b additions and regression assessment

These are additional work, not mandatory original Alpha 1 milestone criteria.

| Addition | Actual implementation/evidence | Assessment |
|---|---|---|
| Sleeper-aligned highlighting | [SpeedZoneRenderer.java](src/main/java/com/benjamintaskis/createsignalextensions/client/SpeedZoneRenderer.java) uses [SpeedZoneSleeperMesh.java](src/main/java/com/benjamintaskis/createsignalextensions/client/SpeedZoneSleeperMesh.java) and Create track graph geometry; V shares R with enforcement. | Implemented; automated mesh/palette checks pass. Live material/curve/slope alignment unverified here. It is not a railway signal map. |
| LEFT/RIGHT running side | [ClientConfig.java](src/main/java/com/benjamintaskis/createsignalextensions/config/ClientConfig.java):28–30 defaults to LEFT; renderer:99–106 reads [SpeedHighlightRunningSide.java](src/main/java/com/benjamintaskis/createsignalextensions/config/SpeedHighlightRunningSide.java) at render time. | Client-only relative-to-travel mapping implemented; 404 config/direction checks pass. No operational speed authority from this setting. |
| Directional pulses | Renderer:96–112 → [SpeedZonePulse.java](src/main/java/com/benjamintaskis/createsignalextensions/client/SpeedZonePulse.java). | Implemented and helper-tested; optional animation leaves base tint. No persistence interaction found. |
| Cosmetic X | Board NBT/GUI still retains `TurnoutMode`; M front/advance/rear logic does not consult it for authority. Renderer treats X as an ordinary boundary. | Implemented; 144 operational/route helper assertions plus boundary/packet checks pass. No new X-specific enforcement path found. |
| Route-specific graph association | R resolves saved explicit block/curve anchors; G tests actual edge/direction/position; M scout uses copied navigation. Renderer:138–144 preserves exact smoothed node coordinates and rejects ambiguous graph mapping. | Nearby-track/turnout route identity correction implemented and helper-tested. F1 exposes a lifecycle regression in the stricter crossing path; association uncertainty cannot be called fully recovered. |
| Graph diagnostics | Common config switch and bounded logger above; manager FRONT/ADVANCE/JUNCTION reasons include graph identity and navigation choice. | Implemented and rate-limit/default-tested. Sampling means not every evaluation is logged. |

Visual projections are no longer used indefinitely from unloaded sign chunks: the graph correction deliberately requires current validation. This affects overlay availability, not saved active train restrictions. Static highlighting can follow compatible possible continuations; it does not claim a reliable selected switch state. Overlay limits/default-speed colours are not evidence of category-specific operational enforcement.

## Executed validation

This audit actually executed the following on Windows with `JAVA_HOME=C:/Program Files/Java/jdk-21` (JDK **21.0.8**):

```powershell
./.gradle-bootstrap/gradle-8.10/bin/gradle.bat clean build --offline --no-build-cache --console=plain --no-watch-fs
```

**Result: BUILD SUCCESSFUL in 36s; 14 actionable tasks, 14 executed.** Java production/test compilation, resource processing, JAR assembly and all existing regression JavaExec tasks ran. `processResources` is intentionally disabled in favour of the existing `safeProcessResources` archive-based task. `BUILD.bat` itself was inspected, not rerun during this audit; its historical successful execution is recorded in the Alpha 1a release notes.

| Executed check | Assertions | Result | Relevant limits of coverage |
|---|---:|---|---|
| `alpha1aChecks` | 25 | PASS | RearClearance helper, zones/mesh/palette and braking simulation. RearClearance is no longer the production graph rear path. |
| `alpha1bChecks` | 221 | PASS | Sleeper/pulse/visual helpers. |
| `cosmeticXChecks` | 144 | PASS | Numeric/prefix parity, board settings and route helpers. |
| `graphAssociationChecks` | 56 | PASS | Real Create graph/edge/TravellingPoint helpers, parallel/fork/crossover/direction, board NBT, replacement/offset/rejection and diagnostics. |
| `runningSideChecks` | 404 | PASS | LEFT default, LEFT/RIGHT, reversed orientation/opposing lanes and TOML handling. |
| `turnoutVisualChecks` | 12 | PASS | Current ordinary/X boundary parity and visual payload compatibility. |
| **Total** | **862** | **PASS** | No executed assertion failed. |

Existing warnings: four production API deprecation/removal warnings, deprecated test API use, Gradle deprecation notice and unavailable advanced terminal features in the headless environment. No compile errors. Full output is in local ignored `build-alpha1-completion-audit.log`.

The build produced `build/libs/create_signalling_additions-1.5.0-alpha.1b-graph-association-test.jar` as a normal build output. This is not a newly corrected mod or a newly packaged release; no production changes were made and no delivery copy was created.

**Coverage boundary:** none of the existing test sources directly exercise `SpeedRestrictionSavedData`, `retainTrains`, `restoreState`, `persistState` or the actual manager's pending-boundary lifecycle. Tests described as saved-world checks in prior notes cover saved **board** settings/anchors, not full saved **train** restriction restoration. Tests that reject null/stale samples correctly validate G's contract; they do not verify that M always supplies a valid history. The green suite therefore does not contradict F1/F2.

## In-game evidence and remaining acceptance tests

[ALPHA_1A_RELEASE_NOTES.md](ALPHA_1A_RELEASE_NOTES.md) and [ALPHA_1B_GRAPH_ASSOCIATION.md](ALPHA_1B_GRAPH_ASSOCIATION.md) explicitly leave runtime acceptance pending. The user has supplied in-game evidence of nearby-track restriction pickup and changing logged target positions in earlier graph-correction work. That establishes historical testing/reporting of a problem, not successful acceptance of the current fix. Original full world/log/NBT evidence for that crossover is unavailable here. The local headless logs and compilation are not in-game acceptance evidence.

No live Minecraft world, reload, deletion, signalling smoke test, visual alignment, TPS profile or multiplayer test was run in this audit. Both reviews are source reviews. Local acceptance still needs:

1. Active lower/route restriction reloads on stopped and moving trains; same UUID and exact saved/restored values; new lower instruction still applies.
2. Long train stopped across one and several pending increases/unrestricted boards; save/reload; release only after actual directional rear passage. Include first movement just before front/rear crossing, after F1 is corrected.
3. Reversal before rear clearance, stop/restart in both directions, graphless/unloaded train return, and a track rebuild overlapping a boundary crossing; safe recovery without nearby-track pickup.
4. The 20→100→60 sequence and monotonic increases with a train spanning multiple sections, after F2 is corrected.
5. Permanently delete a restricted train, allow a 200-tick sweep and inspect removal; simultaneously keep a registered virtual/graphless train and confirm its saved entry remains. This verifies an implemented preventative mechanism, not a reported bug.
6. Straight/parallel/turnout/crossover routes, immediate post-split ordinary and X boards, direction-specific approach braking and both running-side/pulse modes. Verify current graph correction in the actual Station A layout.
7. Confirm no new signalling/manual-control regressions and check loaded/unloaded anchor behaviour without forced chunks. Multiplayer validation remains Beta scope under the roadmap.

Useful automated additions proposed for a later correction are actual train-restriction NBT round-trip/malformed-record/unchanged-dirty/deletion-retention tests and manager lifecycle tests for F1/F2. Their current absence is recorded as a coverage limitation, not a blanket implementation failure.

Historical documentation must be read in context: `ALPHA_1_README.md` still says a confirmed deletion hook is not connected, while the later Alpha 1a release notes describe the implemented 200-tick registry reconciliation. The old README warning is superseded by the actual connected mechanism. Similarly, `ALPHA_1A_INTEGRATION_REVIEW.md` is an earlier WIP snapshot, explicitly superseded by the release notes. Neither is evidence that the current implementation lacks cleanup or compilation. These historical files were left unchanged.

## Independent reviews and reconciliation

- [Review 1 — functional correctness and roadmap compliance](ALPHA_1_COMPLETION_AUDIT_REVIEW_1.md): performed by a separate read-only review agent.
- [Review 2 — persistence, lifecycle, performance and regressions](ALPHA_1_COMPLETION_AUDIT_REVIEW_2.md): performed by a different read-only review agent.

Both reviewers independently inspected the relevant source and roadmap before sharing findings. The primary audit then checked their call-path evidence, the installed Create tick/load source, current tests and the earliest tracked implementation. Review 2 additionally examined registry lifecycle and performance; Review 1 additionally traced first-movement lower-board loss. Both agreed on F1, deleted cleanup completeness and the multiple-boundary F2 trace. The initial distinction between a conservative availability limitation and a functional gap was reconciled: rejecting ambiguous authority is correct, but losing a real required crossing without subsequent recovery leaves lifecycle handling incomplete. No unresolved reviewer disagreement changes the recommendation.

## Acceptance recommendation

**NOT READY.** Original active-state serialization/restoration, stationary handling, validation, deleted-train cleanup, change-only writes and guarded chunk access deserve credit as implemented. Compilation and all 862 assertions pass. However, F1 demonstrably leaves required reload/pending-boundary passage handling incomplete; F2 separately compromises multi-boundary operational correctness. Missing runtime acceptance is additional uncertainty, not the reason these traced implementation gaps are called defects.

The next work should be a narrowly scoped, separately authorized correction of crossing observation/recovery and occupied-section boundary limits, with lifecycle tests and two reviews. No new braking formula, signalling redesign, broad optimisation project, Alpha 2 work or deleted-train cleanup rewrite is justified by this audit. Historical source-archive delivery can be checked independently. After corrections, the remaining local runtime checklist can support reassessment for acceptance.
