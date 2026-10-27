# Final Alpha 1 review 1 — operations, persistence and compatibility

Date: 27 October 2026. Independent reviewer: `alpha1_final_operations`. Read-only review; record prepared by the primary agent from the reviewer's findings.

## Scope and evidence

The accepted Alpha 1b source was frozen in a hash manifest before consolidation. Inspection found exactly seven intended existing-file edits: version/build naming, reproducible archive settings, startup/summary text, logging-only default and two default assertions. Operational manager, SavedData, graph anchors, movement mixin, associations, networking and sleeper rendering are unchanged.

The reviewer traced:

- `TrainBoundaryMixin` HEAD, first `Carriage.travel` and RETURN hooks into the manager's actual begin/finish movement lifecycle.
- Pre-movement restoration, both physical endpoint observations, captured direction/budget and reversal handling.
- Ordered occupied sections, effective minimum restriction and verified rear reconciliation.
- Exact graph associations and bounded recovery without radius/proximity-based crossing guesses.
- `SpeedRestrictionSavedData.load`, manager restore/save, structural dirty suppression and 200-tick cleanup against authoritative Create train UUIDs.
- Existing SavedData name/keys, optional `GraphAnchor`, legacy records without anchors and unchanged packet registration/codecs.
- All `speedDiagnostics()` usages, confirming they gate logging only; explicit existing config values and keys remain preserved.

## Findings

**No confirmed operational, persistence, compatibility or regression blocker.** No corrective operational change was requested. No migration or persistence rewrite was introduced. Saved-data compatibility is supported by unchanged implementations and actual round-trip/validation/legacy tests, not assumed merely from method names.

Known accepted limitations remain: transient 128-movement deferred evidence, conservative handling of changed/ambiguous geometry, and unsafe rollback to pre-correction server semantics while occupied boundaries are pending. These were not presented as newly discovered consolidation defects.

## Validation and outcome

The reviewer inspected successful clean-build logs; the primary agent executed the builds. The final reproducible workspace build passed **1,027 assertions**, including **69 actual manager/lifecycle/SavedData checks**. Subsequent isolated ZIP `BUILD.bat` execution also passed all checks and produced a byte-identical final-version JAR; the final report records the execution evidence.

No in-game or multiplayer test was performed by the reviewer. Source/fixture compatibility does not constitute server-release acceptance. **Final review passes with no confirmed blocker for the user-accepted local Alpha 1 milestone.**
