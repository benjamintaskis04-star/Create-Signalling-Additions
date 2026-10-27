# CSA 1.5.0 — Final Alpha 1 completion report

Date: **27 October 2026**, Australia/Sydney.

**Alpha 1 is complete as the user-accepted local-development milestone.** This package consolidates the accepted Alpha 1b source as internal version **`1.5.0-alpha.1`**. It is not a multiplayer/server release. **Beta 1 remains the planned beginning of server testing.** No Alpha 2 work was started.

Branch: `feature/1.5.0-alpha-1b-visuals`. HEAD remains `6eb32911d525660df34241992c28ca3f22d1ce9f`. The accepted corrections, exporter and consolidation are still uncommitted. No commit, push, merge, branch deletion/rename or change to `main` was performed.

## Completed functionality included

| Area | Consolidated behaviour and evidence |
|---|---|
| Persistent speed restrictions | `SpeedRestrictionManager` restores per-train posted/legacy-route/shunt state and ordered pending boundaries from `SpeedRestrictionSavedData`. Active caps, including stationary trains, survive save/reload. Unchanged states do not mark SavedData dirty. |
| Boundary lifecycle (F1 correction) | `TrainBoundaryMixin` observes both physical endpoints before and after Create movement and captures the actual signed carriage movement command. Rear observation is maintained before the first pending increase; reversal does not join different train ends. Bounded deferred evidence and exact anchors allow verified recovery without spatial guesses. |
| Multiple occupied sections (F2 correction) | Ordered boundary passages retain every still-applicable occupied-section instruction. The effective cap is the minimum of the rear/base and occupied sections, so a long train spanning 20→100→60 cannot jump to 100 when only its rear clears the first increase. Legitimate later increases and unrestricted sections remain possible after verified clearance. |
| Record validation and deleted-train cleanup | Load validation retains valid fields and bounds pending records; malformed optional anchors do not discard valid boundaries. Every 200 ticks, authoritative Create train UUIDs remove records only for permanently deleted trains, retaining registered virtual/graphless trains. Cleanup only marks dirty on change. This mechanism already existed and was preserved. |
| Route-specific graph association and braking | Explicit board anchors resolve to the applicable graph/edge/direction/position. Graph observations and Create navigation distinguish nearby parallel, crossover and diverging routes. Advance braking remains on the train's actual route using the accepted existing formulas. No radius-based speed-board enforcement was reintroduced. |
| Cosmetic X boards | X20/20 and X40/40 share ordinary operational and visual boundary logic. X affects the displayed board only; GUI, models, settings, persistence and placement remain accepted behaviour. No special X turnout enforcement returns. |
| Speed-zone visualisation | Create sleeper-aligned highlighting, directional pulses and straight/curved/sloping routes remain intact. Client `speedHighlightRunningSide` remains LEFT by default, with RIGHT available relative to travel direction. |
| Graph exporter | Operator-only `/csa graph export` remains read-only and command-triggered. Bounded immutable graph/loaded-board snapshots feed asynchronous PNG/JSON/summary output. Coordinates, topology, available curves, dimensions, verified associations and unresolved boards remain represented. |
| Slope-boundary labels | PNG Y annotations are limited to verified slope endpoints, meaningful connected gradient changes and analytic Bezier crests/troughs. Continuous slopes/level track do not receive repeated Y text. Deduplication, collision avoidance, leader lines and marker layering are retained; full XYZ/elevation geometry stays in JSON. |
| Existing equipment and integration | NSW/shunt signalling, Route Interface, speed categories, packets, train movement/physics/braking, sign GUI, models/textures/UVs and client settings are preserved. The Signal Linking Tool remains excluded from `create_signal_extensions:heightvariable`. |

The original roadmap and all audit/corrective/review reports are preserved. Earlier reports accurately describe their historical builds and then-pending acceptance; this report records the user's subsequent Alpha 1 milestone acceptance without rewriting that history.

## Consolidation changes only

Relative to the accepted pre-consolidation source, exactly seven existing files changed:

1. `gradle.properties`: `mod_version=1.5.0-alpha.1`; platform/dependency pins unchanged.
2. `build.gradle`: remove the diagnostic JAR classifier, normalize archive timestamps/order; retain every regression task and existing Windows resource-processing pipeline.
3. `BUILD.bat`: Alpha 1/version banner and temporary workspace naming; bootstrap/copy/build/output behaviour preserved.
4. `CreateSignallingAdditions.java`: correct the stale Alpha 1a startup message to Alpha 1.
5. `config/CommonConfig.java`: `[speed_restrictions] speed_diagnostics` defaults false, with an updated explanatory comment. This key controls logging only; explicit existing true settings are not overwritten. `graph_diagnostics` and `signal_debug` remain opt-in.
6. `diagnostic/GraphExportFiles.java`: summary heading now says Alpha 1. Geometry, JSON, associations, command and rendering algorithms are unchanged.
7. `BoundaryLifecycleChecks.java`: two assertions confirm both speed diagnostic defaults are off. No existing test was removed or weakened.

New documents are this report, `ALPHA_1_BUILD_AND_USAGE.md` and the two independent final review records. No accepted lifecycle, enforcement, signalling or persistence code was deleted as supposed temporary diagnostics: those hooks and anchors are functional. Useful diagnostics remain available; no unrelated logging paths were rewritten.

The accepted elevation-label JAR and final JAR were compared entry-by-entry: **711 existing entries are byte-identical**. Only four entries differ: mod-version metadata, the startup-message class, the default-logging configuration class and the export-summary class. No accepted class/resource was removed.

## Saved worlds, persistence and compatibility

The consolidation leaves `SpeedRestrictionSavedData`, `SpeedBoundaryAnchor`, manager restoration/serialization and the movement mixin unchanged. The SavedData name remains **`create_signalling_additions_speed_restrictions`**. Existing `Trains`, UUID, posted/route/shunt/serial and `PendingBoundaries` fields are unchanged; optional `GraphAnchor` is still optional. Legacy records without anchors remain readable under the accepted verification rules. There is **no new format version, migration, forced rewrite, board replacement or world reset**.

Actual SavedData round-trip, malformed-field/anchor validation, legacy curved-boundary restoration, dirty suppression and authoritative train cleanup are part of the 69-assertion boundary suite. This establishes code/fixture compatibility; no arbitrary production world was loaded by the agent during consolidation.

The existing caveat remains: rolling back to an implementation predating the occupied-section correction while boundaries are pending is unsafe because that old implementation calculates different effective limits. This is an accepted Alpha 1b limitation, not a new migration introduced by `alpha.1` naming.

Pinned build environment: **Minecraft 1.21.1, NeoForge 21.1.243, Create 6.0.10-280 (Create 6.0.10), Java 21.0.8, Gradle 8.10**. Dependency compatibility ranges are preserved. Network registrars, payload versions and codecs are unchanged. This confirms no consolidation wire-format change; it does not certify mixed-version multiplayer or dedicated-server operation. Use a consistent build for later Beta testing.

## Compilation and automated verification actually executed

Workspace command:

```powershell
$env:JAVA_HOME='C:/Program Files/Java/jdk-21'
./.gradle-bootstrap/gradle-8.10/bin/gradle.bat clean build --offline --no-build-cache --console=plain --no-watch-fs
```

**Final workspace result: BUILD SUCCESSFUL in 47 seconds; all 17 actionable tasks executed.** Log: `build-alpha1-final.log`.

| Suite | Assertions | Result |
|---|---:|---|
| Alpha 1a | 25 | PASS |
| Alpha 1b visuals | 221 | PASS |
| Boundary lifecycle / actual manager / SavedData / integration | 69 | PASS |
| Cosmetic X / actual routes | 144 | PASS |
| Slope-boundary elevation labels | 43 | PASS |
| Graph association | 56 | PASS |
| Graph exporter | 53 | PASS |
| LEFT/RIGHT running side | 404 | PASS |
| Boundary/packet compatibility | 12 | PASS |
| **Total per complete run** | **1,027** | **PASS** |

This is the previous 1,025 assertions plus two diagnostic-default assertions. Repeated build runs do not inflate that count. Every existing suite remains attached to `check`; no failing checks were suppressed. Four existing production deprecation/removal warnings, Gradle deprecation warnings and headless terminal-capability warnings remain; no compile errors or assertion failures remain.

### Actual source-ZIP Windows build

A complete 673-file source archive was extracted to an isolated temporary directory and **its own `BUILD.bat` was executed**, rather than merely checking ZIP integrity or rebuilding the original workspace. The script copied the extracted project's build inputs into its own short Windows temporary workspace and ran a clean build with all nine suites.

**Result: BUILD SUCCESSFUL in 52 seconds; 17 actionable tasks, 16 executed and one dependency-artifact task up-to-date. All 1,027 assertions passed.** Log: `build-alpha1-source-zip.log`. The retained script workspace is `C:\Users\damat\AppData\Local\Temp\CSA-Alpha1-26823-26127`.

Its output was exactly `create_signalling_additions-1.5.0-alpha.1.jar` and **byte-for-byte identical** to the workspace JAR, SHA-256:

`d4843cdfa80d1efbbc882505590112bff00707a03be919ea74d04278fec7b684`

The final source ZIP adds this report and the two final review records to those validated inputs, giving **676 entries**. No build input differs from the extracted, successfully built project. Each final ZIP entry is checked against the workspace. The final archive is also used for a further isolated `BUILD.bat` verification before delivery. Its execution evidence is retained in `build-alpha1-final-source-zip.log` and the delivery validation folder; no successful result is inferred from ZIP integrity alone.

The validation reuses the already installed Gradle 8.10 distribution through an external link in the extracted project's ignored `.gradle-bootstrap` directory and existing dependency caches. Those tools/caches are not inside the source ZIP. Fresh-machine Gradle download and an empty dependency cache were not tested; first-time builds require internet access. No new claim of cross-machine or different-JDK byte reproducibility is made.

## Two independent final reviews

[Review 1](ALPHA_1_FINAL_REVIEW_1.md) traced operational correctness, persistence, lifecycle, compatibility and regression protection. **No confirmed blocker** was found. The reviewer verified the consolidation does not change operational manager/SavedData/mixin/association/network/visual code and inspected the executed build log.

[Review 2](ALPHA_1_FINAL_REVIEW_2.md) checked source completeness, resource/bundle parity, build reproducibility, diagnostics and performance. Its reproducibility observation led to explicit normalized JAR timestamps/order, followed by another clean build and actual ZIP-script build. **No confirmed blocker** remains. Reviewers did not edit code, execute these builds themselves or claim in-game testing.

### Performance and diagnostics

No new per-tick work, whole-network scans, chunk loads, disk writes, retained operational history or diagnostic streams were introduced by consolidation. Existing equality-based dirty suppression, 200-tick deletion cleanup and bounded observation recovery are preserved. The existing graph index rebuilding on queried ticks remains an Alpha 5 optimisation opportunity; no redesign was undertaken.

Graph export remains on-command only: 50 ms soft snapshot budget, hard graph/node/edge/board limits, one worker job, bounded sample/output counts, one raster at a time, safe create-new files and cancellation. Live Create structures are only read on the server thread. Cold Bezier caches are not initialized by the exporter. Elevation placement retains 64 labels, 512 visible attempts and bounded per-candidate searches. Graph diagnostics remain rate-limited and disabled by default.

Existing installations explicitly configured with `speed_diagnostics=true` still log until the user turns it off. Changing the default safely reduces new-install logging without silently rewriting a user's choices.

## User acceptance, known limitations and future runtime scenarios

The user explicitly accepted Alpha 1 as a complete local-development milestone. This is the acceptance evidence for this consolidation. The agent did not perform additional in-game testing during this task, and no itemized user runtime-test log was supplied. Automated execution, source inspection and user acceptance are distinguished here; individual runtime cases are not falsely labelled as agent-observed successes.

Unchanged known limitations:

- Deferred front-crossing evidence is transient and bounded to 128 movement observations; evidence can expire or disappear on shutdown before association verification. No crossing is invented when evidence is insufficient. Pending boundaries themselves remain persisted.
- Exact equivalent geometry can re-associate; arbitrary subdivision, modified curves and ambiguous/removed track remain conservative. Legacy directions that cannot be verified stay restricted. Previously unknown boards in unloaded chunks are not discovered by force-loading them.
- Export capture is bounded and its deadline is soft. Only loaded/registered boards are included. Cold/unavailable curves, incomplete topology and ambiguous attachments are explicit omissions/unresolved states. Count limits and tile budgets can omit detail coverage.
- Crowded PNGs can omit elevation labels; missing geometry is not guessed. Full XYZ remains in JSON, and projected crossings never establish connectivity.
- No large-live-network TPS benchmark, fresh-cache bootstrap test or multiplayer/dedicated-server acceptance was performed.

Useful ongoing local regression scenarios and later Beta coverage remain:

1. Moving and stationary reload immediately before front/rear board crossings; stop/reverse/resume; first increase after an empty queue.
2. Long trains spanning 20→100→60, repeated increases/reductions and unrestricted boards, including save/reload and opposite travel.
3. Station A crossover, close parallel tracks and immediate post-split ordinary/X boards; confirm actual-route anticipation/braking and no adjacent-route pickup.
4. Temporary sign/graph unavailability, equivalent reconstruction and genuinely changed geometry; multiple dimensions and stop-at-signal/station behaviour.
5. Preserved NSW/shunt/Route Interface operation, categories, GUI/models, LEFT-default/RIGHT sleeper lanes and directional pulses.
6. Real-world exporter accuracy, slopes/overpasses, operator permissions, filesystem failures and larger layouts.
7. **Beta 1:** multiplayer/server compatibility, profiling, concurrent players, dedicated-server/headless operation and long-running lifecycle testing.

These are ongoing regression/coverage notes and accepted limitations, not a reversal of the user's local Alpha 1 milestone acceptance. They do not start Alpha 2 or move server testing into an alpha.

## Final deliverables and preservation

Delivery directory: `dist/alpha-1/`.

- `create_signalling_additions-1.5.0-alpha.1.jar`
- `CSA_1.5.0_Alpha_1_Source.zip` — complete source, resources/templates, static resource bundle, build configuration, `BUILD.bat`, all regression sources, roadmap and all historical/final reports.
- `ALPHA_1_FINAL_COMPLETION_REPORT.md`
- `ALPHA_1_FINAL_REVIEW_1.md`, `ALPHA_1_FINAL_REVIEW_2.md`, `ALPHA_1_BUILD_AND_USAGE.md`
- `SHA256SUMS.txt` — SHA-256 for each delivered file above, including the JAR, source ZIP and report. The checksum file intentionally does not hash itself.

The ZIP excludes Git metadata, caches, downloaded Gradle/dependencies, generated JARs/classes, build directories and local logs. The resource bundle is intentionally included as a required build input. Deliverable ZIP/JAR/checksums are ignored generated output and are not proposed for Git.

All **48 files in the previous delivery folders** were hashed before consolidation and are preserved unchanged. All earlier Alpha 1b builds, original audit documents and corrective/review reports remain present. The approved roadmap and public-description README were not rewritten.

## Final working-tree state and exact proposed commit set

Final branch/HEAD are unchanged. **Nothing is staged; 35 files would be committed if subsequently approved: 9 modified tracked files and 26 currently untracked files.** This list includes the accepted work that was already uncommitted, not only the small consolidation changes. No commit has been made and no approval to commit/push is inferred from milestone acceptance.

Modified tracked files:

```text
BUILD.bat
build-support/static-resources.zip
build.gradle
gradle.properties
src/main/java/com/benjamintaskis/createsignalextensions/CreateSignallingAdditions.java
src/main/java/com/benjamintaskis/createsignalextensions/config/CommonConfig.java
src/main/java/com/benjamintaskis/createsignalextensions/content/speed/SpeedRestrictionManager.java
src/main/java/com/benjamintaskis/createsignalextensions/content/speed/SpeedRestrictionSavedData.java
src/main/resources/create_signal_extensions.mixins.json
```

New files:

```text
ALPHA_1_COMPLETION_AUDIT.md
ALPHA_1_COMPLETION_AUDIT_REVIEW_1.md
ALPHA_1_COMPLETION_AUDIT_REVIEW_2.md
ALPHA_1B_BOUNDARY_LIFECYCLE_CORRECTION.md
ALPHA_1B_BOUNDARY_LIFECYCLE_REVIEW_1.md
ALPHA_1B_BOUNDARY_LIFECYCLE_REVIEW_2.md
ALPHA_1B_GRAPH_EXPORTER_REPORT.md
ALPHA_1B_GRAPH_EXPORTER_REVIEW_1.md
ALPHA_1B_GRAPH_EXPORTER_REVIEW_2.md
ALPHA_1B_GRAPH_ELEVATION_LABELS.md
ALPHA_1B_GRAPH_ELEVATION_LABELS_REVIEW_1.md
ALPHA_1B_GRAPH_ELEVATION_LABELS_REVIEW_2.md
ALPHA_1_BUILD_AND_USAGE.md
ALPHA_1_FINAL_COMPLETION_REPORT.md
ALPHA_1_FINAL_REVIEW_1.md
ALPHA_1_FINAL_REVIEW_2.md
src/main/java/com/benjamintaskis/createsignalextensions/content/speed/SpeedBoundaryAnchor.java
src/main/java/com/benjamintaskis/createsignalextensions/diagnostic/GraphElevationLabels.java
src/main/java/com/benjamintaskis/createsignalextensions/diagnostic/GraphExportBoards.java
src/main/java/com/benjamintaskis/createsignalextensions/diagnostic/GraphExportCommand.java
src/main/java/com/benjamintaskis/createsignalextensions/diagnostic/GraphExportFiles.java
src/main/java/com/benjamintaskis/createsignalextensions/diagnostic/GraphExportSnapshot.java
src/main/java/com/benjamintaskis/createsignalextensions/mixin/TrainBoundaryMixin.java
src/test/java/com/benjamintaskis/createsignalextensions/content/speed/BoundaryLifecycleChecks.java
src/test/java/com/benjamintaskis/createsignalextensions/diagnostic/GraphElevationLabelChecks.java
src/test/java/com/benjamintaskis/createsignalextensions/diagnostic/GraphExporterChecks.java
```

**Recommendation: approve the final consolidated Alpha 1 local-development package for the accepted milestone.** Commit/push/merge remain separate actions requiring explicit approval. Multiplayer/server release approval is outside this milestone and remains planned for Beta 1.
