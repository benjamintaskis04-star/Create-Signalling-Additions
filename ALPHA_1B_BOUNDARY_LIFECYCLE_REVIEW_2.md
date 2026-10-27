# Boundary lifecycle correction — independent review 2

Date: 27 October 2026 (Australia/Sydney). Baseline: `6eb32911d525660df34241992c28ca3f22d1ce9f`.

A different independent read-only reviewer assessed persistence, lifecycle, compatibility, performance, source/bundled mixin registration and regressions. No files or builds were changed/run by the reviewer. Findings and final re-review are recorded here.

Confirmed findings corrected:

- Actual endpoint dimension dispatch replaces reliance on Create's overworld tick argument.
- Legacy boundary migration/new direction snapshots use the tangent at the actual edge position.
- Per-board and rear cursors skip previously processed journal prefixes; normal processing does not repeatedly evaluate 128 old movements.
- Distinct coincident boards now use the most restrictive verified rear-clearance result, including after reload with distinct saved queue indices.
- A late-verified coincident instruction is allowed to supply actual front/rear evidence at the same already-cleared order. Its clearance preserves the existing lower coincident limit. Separate late-lower and late-higher tests protect both cases.
- Historical replay uses frozen same-edge/direct-adjacency evidence and does not infer an old intermediate route from present topology.

Verified positively:

- Existing saved records remain readable; `GraphAnchor` is optional. Invalid anchor data does not delete a valid pending boundary.
- Anchors and graph lookups operate in memory without forced chunks or nearest-edge selection.
- Entry/Boundary/anchor structural equality still suppresses unchanged dirty marking.
- Source and actual `static-resources.zip` both register the common observation mixin.
- No network/config-key/client visual/model/texture/UV changes were introduced.

Final re-review outcome: **no remaining release-blocking finding in the reviewed persistence, lifecycle, compatibility and performance scope**, conditional on the primary author's final successful build/tests. The reviewer's earlier test-log inspection preceded the final late-coincident and curve cases; final execution is documented by the primary author.

Limitations that must remain visible:

- The journal retains at most 128 movement observations and is not persisted; unresolved front evidence can expire or be lost on shutdown.
- Exact endpoint/geometry reconstruction does not prove arbitrary subdivision/changed topology.
- Old records without usable anchors require verified original links; uncertain cases stay restrictive.
- Older server binaries do not understand new queued-reduction effective-limit semantics; rollback while spanning such sections is unsafe.
- Runtime mixin application, moving-world reloads, real track reconstruction and live performance still need acceptance testing.

The full new coverage and final execution results are in [the correction report](ALPHA_1B_BOUNDARY_LIFECYCLE_CORRECTION.md). Neither review constitutes in-game acceptance.
