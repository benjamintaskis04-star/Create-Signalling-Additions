# Alpha 1 completion audit — independent review 2

Date: 27 October 2026 (Australia/Sydney).
Commit: `6eb32911d525660df34241992c28ca3f22d1ce9f`.
Reviewer: different read-only persistence/lifecycle/performance review agent. No files changed by the reviewer. Report recorded by the primary auditor from the review findings.

## Emphasis and evidence

Read the entire approved roadmap and inspected persistence lifecycle, actual manager execution, graph association, visual synchronization, dirty flags, world-access guards, retained state, diagnostics and tests. Used installed Create 6.0.10 sources to verify complete train registry loading/removal. References use [the main audit's source key](ALPHA_1_COMPLETION_AUDIT.md).

## Deleted trains — complete preventative requirement

M:114–144 prunes saved entries every 200 ticks using `Create.RAILWAYS.trains.keySet()`, not the filtered per-tick scan set. D:38–39 removes absent UUIDs and dirties only on removal. Graphless and virtual trains are retained while registered. M:141–144 only clears their transient evaluation state.

Create's GlobalRailwayManager.levelLoaded/loadTrackData:82–98 obtains the full saved train registry before ordinary ticking. `removeTrain`:121–127 removes deleted registry entries; disassembly calls that mechanism. This already implements the original requirement. **No deleted-train cleanup bug found**, and the user has not reported one. A live deletion test would confirm acceptance evidence, not add missing functionality.

## Persistence and lifecycle

D validates records and stores active/pending state. M:147–179 restores and persists it; automatic first-command capping restores from saved state. Server stop clears static state/caches at M:87–92. D:58–64 compares records before dirty marking; no confirmed redundant restriction disk writes.

Independently confirmed F1: M:405–407 returns before rear observation while no pending boundary exists, and restored state has no previous rear graph sample. If the rear crosses before an initial usable sample, G rejects it and later samples cannot recreate the passage. Rebuild/availability loss can have the same conservative stuck-cap consequence. Review 1 separately traced the analogous first-movement lower-board miss. Safe rejection alone does not complete recovery.

The 20→100→60 queue sequence was raised during this review and independently checked by review 1 and the primary auditor. M compares each new board to the still-active 20, then rear assignment can apply 100 while the train already spans 60. Classify as a traced boundary correctness issue, not an observed incident or deletion/persistence serialization bug.

## Performance and access

- R:37–45,73–94 rebuilds a full network index per queried level/game tick, including unchanged geometry. This avoids per-board full rebuilds but still repeats whole-network work. Its necessity/cost requires assessment; no measured TPS failure.
- M:319–337 scans loaded boards per train, M:456–467 builds scout snapshots, M:548–562 checks boards each one-block step, even for stopped trains. Look-ahead is bounded to 640–8192 blocks.
- M:174–179 constructs pending projections and D:58 a next Entry each train tick. These are confirmed transient allocations, not extra disk writes.
- V uses change-only dirty marking. S runs once per 20 ticks and sends only changed per-player snapshots; disconnected entries are pruned.
- `hasChunkAt` guards relevant world access in M:247,522; R:50; L:97. No forced chunk loading found in audited speed-state/association paths.
- Graph diagnostics default off and enforce 10 messages/20 ticks, 100-tick per-key spacing and 2048 keys. Legacy speed diagnostics default on and scale with train count; they are controllable but lack a global cap.
- Restriction state follows registry lifetime and caches clear at shutdown. No confirmed unbounded leak. Separate inherited category/shunting stores are not evidence of missing restriction-record cleanup.

Do not convert possible optimizations into measured performance defects, or shift the whole Alpha 5 milestone into this audit. The repeated network scan is a real comparison point against the cross-cutting roadmap principle.

## Validation and reconciliation

The primary audit actually executed clean build/check with Java 21: 862 assertions passed. Existing tests do not exercise actual train restriction SavedData, `retainTrains` or manager lifecycle; T1's rear helper is no longer the production graph path. This coverage limit explains why F1/F2 can coexist with passing tests; it does not disprove implemented persistence/cleanup.

Both reviews agree on cleanup completeness, change-only writes, F1 and the F2 source trace. Correct handling of ambiguity must remain conservative, but recovery from missed required crossings remains incomplete. No unresolved disagreement affects readiness.

## Recommendation

**NOT READY** because the pending-boundary lifecycle gap is demonstrably incomplete. Propose the narrow sampling/recovery and occupied-section corrections described in the main report, then lifecycle regressions and local runtime acceptance. No production or performance change was made or authorized by this audit.
