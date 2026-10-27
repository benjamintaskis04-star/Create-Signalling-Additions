# Alpha 1b boundary lifecycle correction

Date: 27 October 2026 (Australia/Sydney).
Branch: `feature/1.5.0-alpha-1b-visuals`.
Baseline/unchanged HEAD: `6eb32911d525660df34241992c28ca3f22d1ce9f`.
Internal version: **1.5.0-alpha.1b**.

This is an uncommitted corrective build for audit findings F1/F2, not Alpha 2. The three original completion-audit documents and approved roadmap are preserved unchanged. No commit, push, merge or branch deletion was performed.

## F1: cause and correction

The old manager first observed graph positions in `ServerTickEvent.Post`. Installed Create 6.0.10-280 source confirms that `CommonEvents.onServerWorldTick` calls its railway manager in `LevelTickEvent.Post`; that manager calls `Train.earlyTick` (including reattachment), then `Train.tick`. Navigation and carriage movement happen before CSA's server-post observation. Create restores saved nonzero speed, so the first movement cannot be assumed stationary. The old rear scan also returned before sampling whenever its pending queue was empty.

The new common `TrainBoundaryMixin` observes both physical train ends at `Train.tick` HEAD and RETURN. HEAD restores restriction state and freezes graph observations before navigation/movement. A separate hook immediately before the first `Carriage.travel` captures the signed movement command once: stations, signals or blocked motion may set train speed to zero before RETURN. Direction and the crossing budget therefore reflect the movement that occurred, rather than an unreliable post-stop command. Both physical ends are captured before choosing the actual leading/trailing end, which avoids joining different carriage ends across a reversal.

The manager maintains rear observations even with no pending increase. Its production begin/end methods are the same methods called by the headless lifecycle regression suite. Operational enforcement and shunt scanning remain server-side; the mixin observes movement and does not replace Create's navigation, signalling or physics.

Create passes the overworld into `Train.tick` for all trains. Board lookup now explicitly uses both physical endpoints' actual graph dimensions, including a portal-spanning train, and retains pre-movement board descriptors for the movement being evaluated.

Crossings still require actual oriented graph edges and positions. No proximity pickup, nearest-edge search or forced chunk load was introduced. The existing graph resolver/index was not redesigned.

For temporary association failures, the manager retains **at most 128 actual movement observations** per train; stationary ticks do not evict movement evidence. Board cursors avoid evaluating old frames again during normal processing. When the same configured board can be verified, recorded front passages can be reconciled in chronological order. A changed configuration/link starts a new observation cursor rather than applying today's settings to an unverifiable older instruction.

Pending sections capture an optional `GraphAnchor`: exact oriented node locations (including dimension and smoothing offset), edge length/curve geometry and edge position. These anchors are saved with the existing boundary records. A verified anchor can clear a pending boundary without requiring the sign's chunk or BE to remain loaded. Equivalent graph objects can be reconstructed using exact node lookup and geometry checks; UUID/object replacement alone does not destroy the association.

Historical/rebased recovery proves only an observed same edge or directly adjacent edge transition with matching frozen geometry. It never infers a past intermediate route from today's topology. Missing/removed/changed geometry, ambiguity, insufficient history or unverifiable legacy records remain conservative. A post-movement initial sample alone is never proof of passage or non-passage.

## F2: cause and correction

Previously, each new board was compared only with the current effective cap. Under 20, both 100 and 60 could therefore be queued as increases. Rear clearance of 100 directly assigned 100 and ignored the 60 section already occupied farther along the train. Lower instructions also discarded the earlier pending queue, losing section history.

The existing ordered boundary list now represents **all entered sections still spanned by the train**, including reductions. The stored posted value represents the rear/base section; `TrainSpeedState.effectiveLimitKmh` takes the minimum of that base, every entered section still represented, and the unchanged route/shunt constraints. Unrestricted is the absence of a numeric cap, not permission to clear an occupied lower section early.

For **20 → 100 → 60**, the cap remains 20 while any relevant part is still in 20. When the rear clears the 100 boundary, the later occupied 60 section still constrains the train, so the cap becomes 60. Consecutive increases still release legitimately after whole-train clearance. The implementation does not retain the lowest speed ever encountered.

A verified trailing passage supersedes the preceding instruction prefix because the complete train has entered the new instruction. Later entered sections remain represented. This also permits a valid instruction encountered in the opposite direction to supersede earlier-direction state once the whole train clears it. Reversal alone does not invent clearance or discard a saved pending instruction.

Distinct coincident boards have no meaningful physical order: the most restrictive verified instruction wins. Rear clearances at the same physical crossing are grouped, including after reload. Late-verified coincident lower or higher instructions preserve that minimum instead of being discarded by a tie guard or overriding an already established lower limit.

## Changed source and build files

| File | Change |
|---|---|
| [SpeedRestrictionManager.java](src/main/java/com/benjamintaskis/createsignalextensions/content/speed/SpeedRestrictionManager.java) | `beforeTrainMovement`, `beforeCarriageTravel`, `afterTrainMovement`; actual-dimension board dispatch; shared production `beginBoundaryMovement`/`finishBoundaryMovement`; ordered `enterBoundary`/`reconcileRear`; bounded observation/cursor state; occupied-section effective minimum; testable actual `restoreState`/`saveState`; focused diagnostics. Old speed-board post-tick scanner/release path removed; unrelated post-tick shunt handling retained. |
| [SpeedBoundaryAnchor.java](src/main/java/com/benjamintaskis/createsignalextensions/content/speed/SpeedBoundaryAnchor.java) | Freeze, validate, serialize and exactly resolve oriented edge geometry/position without a world search. |
| [SpeedRestrictionSavedData.java](src/main/java/com/benjamintaskis/createsignalextensions/content/speed/SpeedRestrictionSavedData.java) | Optional `GraphAnchor` boundary field; existing record validation, cleanup and structural dirty suppression retained. |
| [TrainBoundaryMixin.java](src/main/java/com/benjamintaskis/createsignalextensions/mixin/TrainBoundaryMixin.java) | Observation hooks around pinned Create movement lifecycle; no movement alteration. |
| [create_signal_extensions.mixins.json](src/main/resources/create_signal_extensions.mixins.json) | Register common observation mixin. |
| `build-support/static-resources.zip` | Same mixin registration in the resource bundle actually used by this Windows build. All 467 entries compared with baseline: only the mixin JSON contents changed. |
| [BoundaryLifecycleChecks.java](src/test/java/com/benjamintaskis/createsignalextensions/content/speed/BoundaryLifecycleChecks.java) | Actual manager lifecycle/state and real restriction SavedData checks, plus compiled Create/mixin integration inspection. |
| [build.gradle](build.gradle) | New mandatory `boundaryLifecycleChecks` task and distinct `boundary-lifecycle-test` JAR classifier. |

No changes to `BUILD.bat`, platform versions, GUI, board settings/category selection, cosmetic X, client config, network packets, sleeper/pulse rendering, models, textures, UVs, signalling, Route Interface, braking envelope/formulas or train physics. The Signal Linking Tool remains absent from the heightvariable tag in both source and build bundle.

## Tests and actual build results

Before production changes, focused checks were written and executed against the audited source. Both failed as expected:

- F1: actual manager lacks a pre-movement observation lifecycle (`build-boundary-baseline-f1.log`). This initial structural check establishes the missing hook; subsequent tests exercise real manager begin/end behaviour.
- F2: actual `TrainSpeedState.effectiveLimitKmh` returns 100 with an occupied 60 boundary still queued (`build-boundary-baseline-f2.log`).

The completed suite uses real Create graph/edge/TravellingPoint objects, production manager transitions, real SavedData serialization/load/update/cleanup and the compiled dependency/mixin descriptors. It does not substitute a separate implementation of the boundary rules. It does not start Minecraft or apply mixins to a running world.

New coverage includes moving reload immediately before front/rear crossings; first increase after an empty queue; stationary reload/resume; reversal and resumption; temporary association failure/recovery; sign unavailability; saved anchor and equivalent graph replacement; removed-track rejection and verified recovery; nearby parallel/opposite-direction rejection; bounded history and stopped-history retention; post-only initialization rejection; changed-intermediate-topology rejection; multiple increases/reductions/unrestricted sections; 20→100→60 forward and reverse; stop/reload/rebuild; reversal supersession; coincident and late-recovered coincident instructions; ordinary curved legacy migration and curve-anchor round-trip; actual restriction NBT round-trip/malformed fields; unchanged dirty suppression; authoritative deleted-record cleanup; actual dimension dispatch; captured stop-time direction/budget; installed Create movement descriptor and actual bundled mixin registration.

Final clean build command (JDK 21.0.8):

```powershell
$env:JAVA_HOME='C:/Program Files/Java/jdk-21'
./.gradle-bootstrap/gradle-8.10/bin/gradle.bat clean build --offline --no-build-cache --console=plain --no-watch-fs
```

**Final result: BUILD SUCCESSFUL in 37s; 15 actionable tasks, all 15 executed.** Production and test compilation, resource expansion, JAR creation and all regression tasks completed successfully. The existing six suites contain 862 assertions; the new boundary suite contains 67 focused assertions, for **929 total, all passing**.

| Suite | Assertions | Result |
|---|---:|---|
| Alpha 1a | 25 | PASS |
| Alpha 1b visuals | 221 | PASS |
| Cosmetic X | 144 | PASS |
| Graph association | 56 | PASS |
| Running side | 404 | PASS |
| Boundary/packet compatibility | 12 | PASS |
| New boundary lifecycle / manager / SavedData / integration | 67 | PASS |

The final log is `build-alpha1b-boundary-lifecycle.log` (local ignored build output). Existing API/Gradle deprecation and headless terminal warnings remain; there were no compile errors or failing assertions. `BUILD.bat` remains available for a local Windows build, including Gradle bootstrap and the required static resource bundle; the script itself was inspected, while the Gradle clean build above was actually executed.

## Saved worlds and client/server compatibility

- Existing train UUIDs, active posted/legacy-route/shunt restrictions, pending limits/order/sign/dimension/serial/target/forward fields remain readable. Existing boards keep their GUI/settings/links and do not need replacement.
- `GraphAnchor` is optional. Older boundaries without it migrate only when their frozen target and direction can be verified against their current original board association. An invalid optional anchor does not discard the otherwise valid pending record.
- Legacy directional verification now uses the tangent at the board, rather than the start-of-edge chord. Ordinary curved legacy migration is tested. Unusually large bends where old saved chord direction cannot establish local direction remain conservative; no successful arbitrary legacy migration is claimed.
- New occupied-section semantics store a rear/base posted value and represent reductions in the pending list. **Do not roll the server back to an older implementation while such sections are pending:** the old code does not calculate the same effective minimum. Back up the test world before rollout.
- Network packet formats and internal mod version are unchanged. The **server must update** for operational fixes. Previous graph-association clients remain wire-compatible; no client visual change is required by this correction. Matching client/server builds are recommended for test consistency; this is not a claim of multiplayer acceptance.

## Performance and diagnostics

No per-train whole-network scan was added. The existing once-per-queried-level/tick graph index remains unchanged; its broader invalidation/reuse opportunity remains Alpha 5 work. Anchors use exact graph node lookup, and pending bindings are reused while live. Edge identity data is shared between observations until the observed edge changes. Movement storage is bounded; cursor metadata for boards no longer registered expires after 128 train ticks, preventing accumulation while stopped. Registered boards with unresolved associations keep their cursors during stops. Diagnostics retain their existing global/per-key limits. Temporary board snapshots and per-train observations add work, which has not been profiled on a large live layout.

Unchanged saved entries still compare equal and do not mark dirty. New boundary/anchor updates legitimately change saved state. No synchronous chunk loading was introduced.

The existing disabled-by-default `[speed_restrictions] graph_diagnostics` switch controls FRONT/REPLAY/REAR verified-passage and association-observation output. No new config key or unconditional diagnostic stream was introduced. The older `speed_diagnostics` option remains independent and retains its existing default.

## Independent reviews

[Review 1](ALPHA_1B_BOUNDARY_LIFECYCLE_REVIEW_1.md) assessed movement, crossings and boundary semantics. [Review 2](ALPHA_1B_BOUNDARY_LIFECYCLE_REVIEW_2.md) separately assessed persistence, lifecycle, compatibility and performance. Confirmed findings were corrected and affected checks expanded/repeated. Both final reviews found no remaining release-blocking issue in their reviewed scope, conditional on the final successful build/tests and runtime acceptance. Neither reviewer claimed to run an in-game test.

## Remaining limitations and local acceptance checklist

The journal is transient and bounded to 128 movement observations. Unknown front crossings cannot be recovered after evidence expires or the server shuts down before association verification; a board absent from the registry for more than 128 train ticks also loses its deferred front cursor. No instruction is invented from geography in those cases. Anchored pending boundaries themselves are saved and retained; their clearance still requires valid crossing evidence.

Equivalent exact edge geometry can rebind across graph object/UUID replacement. Arbitrary edge subdivision, modified curves and changed intermediate topology are not proven equivalent. Ambiguous/missing track and legacy links that cannot be verified retain conservative restrictions. Boards never known/registered while their chunks are unloaded cannot provide a new operational instruction. The existing SavedData load bounds remain unchanged.

No live mixin application, real moving-world reload, rendered visual alignment, dedicated-server rollout or TPS benchmark was performed here. Before accepting Alpha 1, use a backed-up test world to:

1. Reload a moving train just before its front reaches a lower ordinary/X board and just before its rear clears an increase. Confirm exact limits and directional passage in graph diagnostics; repeat stopped/restarted.
2. Span 20→100→60, monotonic increases, an intermediate lower section and unrestricted boards with a long train. Confirm 20→60 release in the example and legitimate final increases; repeat reverse travel and save/reload while spanning boundaries.
3. Reverse before clearance, stop, resume and pass an applicable opposite-direction instruction. Confirm no false release and no permanent retention of an older limit after verified whole-train supersession.
4. Temporarily unload a known sign/anchor and rebuild equivalent track while a boundary is pending. Confirm exact recovery; check changed/subdivided/ambiguous geometry remains conservative and does not attach to nearby tracks.
5. Run the Station A crossover straight/diverging approaches, nearby parallel tracks, immediate post-split boards, both directions, curves/slopes, and boards with different categories. Verify advance braking remains on the selected route.
6. Repeat the lifecycle cases in another dimension; confirm actual graph-dimension selection and stop-at-signal/station behaviour.
7. Check unchanged GUI, X display, NSW/shunt/Route Interface behaviour, LEFT-default/RIGHT sleeper lanes and directional pulses. Confirm normal logs are quiet with graph diagnostics disabled.
8. Check stationary active-cap restoration and the existing 200-tick deleted-train cleanup alongside a registered virtual/graphless train. Cleanup was not rewritten.

## Updated completion assessment

**READY SUBJECT TO TESTING** for final **local Alpha 1 in-game acceptance testing**. The final clean build and all 929 assertions passed. The original audit remains a truthful record of baseline defects. F1/F2 now have corrective implementations and manager/SavedData regressions; no original audit text was rewritten to hide them. Alpha 1 is **not accepted** until the runtime checklist passes. Alpha 5 profiling and Beta multiplayer testing remain separate milestones.

## Corrective package

JAR: `dist/alpha-1b-boundary-lifecycle/create_signalling_additions-1.5.0-alpha.1b-boundary-lifecycle-test.jar`.

JAR SHA-256: `d72d4758279dd83d55facc3a6fe5820a1f54f40f93e83663f8c36416cba7caa5`.

Complete source/build package: `dist/alpha-1b-boundary-lifecycle/CSA_1.5.0_Alpha_1b_Boundary_Lifecycle_Test_Source.zip`.

The package also contains this report, both review reports and `SHA256SUMS.txt`. Generated JARs, source delivery ZIP and build outputs remain outside Git. No source commit has been created.

JAR inspection verifies internal Alpha 1b metadata, common mixin registration, exclusion of regression classes and exclusion of the Linking Tool from the packaged heightvariable tag. Compared with the previous graph-association JAR, 197 existing compiled classes are byte-identical, including the preserved client visuals, GUI, network, signalling and braking code. Changed existing classes belong to the manager/SavedData implementation; the anchor/mixin and associated nested classes are new.
