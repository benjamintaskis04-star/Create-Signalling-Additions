# Alpha 1 completion audit — independent review 1

Date: 27 October 2026 (Australia/Sydney).
Commit: `6eb32911d525660df34241992c28ca3f22d1ce9f`.
Reviewer: separate read-only functional/roadmap review agent. No files changed by the reviewer. Report recorded by the primary auditor from the review findings.

## Emphasis and evidence

Read the complete approved roadmap. Traced actual event registration, SpeedRestrictionManager, SpeedRestrictionSavedData, Navigation mixin, graph resolver/location, train direction and existing regression sources. Inspected installed Create 6.0.10 source for registry loading/removal, tick order and saved motion. Later compared findings with the second reviewer and the primary auditor.

Source paths and line references use the reference key in [the main audit](ALPHA_1_COMPLETION_AUDIT.md).

## Implemented requirements

- Active posted/legacy-route/shunt restrictions serialize and restore directly: D:26–29,68–103,107–139; M:147–179. Stationary eligible trains are restored; M:123–138 does not require movement.
- N:27–36 → M:763–783 restores before automatic navigation consumes its capped target.
- Ordered pending instructions retain saved limit, sign, dimension, route serial, target and direction: D:117–127; M:160–167.
- UUID/dimension/limit/position/vector/key/count validation is connected to load: D:68–95,142–146.
- **Permanently deleted train cleanup is complete:** M:117–120 reconciles against Create's full registry every 200 ticks; D:38–39 dirties only when removing something. Graphless/virtual registered trains retain records. No deleted-train bug identified.
- D:51–65 prevents unchanged dirty marking. Chunk access is guarded. Graph identity/direction and copied navigation prevent nearby-track authority and avoid mutating real navigation paths.

## Functional findings

### F1: first-movement/observation lifecycle

M:147–170 restores no graph samples; M:233–235 observes front only after Create movement. M:405–417 samples rear only while pending. G:60–69 rejects null/stale history and requires a crossing. Create's CommonEvents:133–142 / Train:289–324 shows movement before CSA ServerTick.Post; Train.read:1223 restores nonzero speed.

A lower board crossed on first movement after reload can be missed; a restored pending increase whose rear crosses on that tick can remain stuck. Temporary unavailable/rebuilt associations can lose a rear passage similarly (M:413–431). This is a source-demonstrated conditional failure, not an in-game reproduction. Merely sampling rear every post-tick does not fix first reload movement.

The old radius helper could sometimes mask missing history with a later nearby observation. Preserve the graph identity correction when fixing lifecycle; do not reintroduce radius authority.

### F2: occupied-section constraint

For a long train under 20, front passage of 100 then 60 queues both because each compares against active 20 (M:355–375). Rear passage of 100 directly assigns 100 (M:432), despite the front already being beyond 60. This deterministic trace was reconciled with review 2. The pattern predates the graph correction in earliest tracked source `1123dce`; no runtime incident is claimed.

## Test and acceptance limits

The primary audit executed a successful clean Java 21 build and 862 assertions. No test directly references actual restriction SavedData load/update/cleanup or manager restoration/pending lifecycle. Old T1 rear tests exercise the obsolete production helper, while TG tests graph contracts/board anchors. Missing tests do not negate the persistence/cleanup features actually traced.

Propose valid graph sampling before first movement, continuous rear history and safe association-loss recovery, plus occupied-section limit preservation. Add manager lifecycle/round-trip tests; leave GUI, cosmetic X, braking formulas, physics, signals and rendering unchanged. No corrections were implemented during this audit.

## Recommendation

**NOT READY:** F1 leaves original Alpha 1 reload/pending-transition requirements partially complete; F2 separately needs correction. Credit deleted-train cleanup as complete. Runtime acceptance is still pending independently of these source findings.
