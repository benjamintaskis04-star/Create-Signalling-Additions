# Issue #3 review 2 - route classification, rendering and package

Second, separate self-review completed 27 October 2026 after review 1.

Reviewed the complete renderer diff, TurnoutVisualRoutes, the actual installed
Create 6.0.10-280 TravellingPoint/TrackEdge sources, new regression tests and
corrective artifact naming. This is not an independent reviewer or game test.

Verified:

- Ordinary boards alone populate the ordinary boundary map; turnout boards are
  separate starts, so an X20 cannot terminate or replace the normal 100 km/h zone.
- Waiting X walks emit no approach geometry. A single exit retains waiting state;
  a unique facing split only enqueues non-main exits with waiting disabled. An
  ambiguous split enqueues no X overlay. Later active walks follow compatible
  continuations and stop at the existing ordinary/unrestricted boundaries.
- Classifier uses Create's first/last-quarter oriented trajectories and lateral
  dot ranking with the junction normal. It never substitutes edge.isTurn() or
  an arbitrary fixed world axis for route classification. Ties are deliberately
  unknown, rather than Create's adjacency-order fallback. Tests include swapped
  adjacency order, all compass/diagonal rotations, mirrors, reversed approaches,
  slope normals, curves and invalid/ambiguous inputs.
- Read-only graph traversal retains dimensional checks, cycle guards and work
  budgets. No train navigation, steering, switch state or edge data is mutated.
- The normal zone and diverging X overlay share the same cached physical sleeper
  surfaces; existing per-direction lower-speed ownership gives main 100/green
  and diverging 20/blue. There is no second coincident render submission.
- Inspection originally selected by the winning zone's identity, which would
  hide an X overlay when inspecting its normal approach. Cached physical-face
  membership now lets the selected normal zone display the correct X ownership.
- Pulse direction, phase-distance accumulation and canonical physical-edge
  geometry are retained. Sleeper model extraction, rails and shaders are unchanged.

Review improvement applied: replace the new per-face inspection stream with a
small allocation-free loop over the selected zones, retaining cached identity
sets for membership. Re-run clean build/check after this source edit before
delivery. No unresolved implementation defect was found in this pass.

Residual limitations remain documented in ALPHA_1B_ISSUE_3_TURNOUT_VISUALS.md:
unknown main on symmetric Y, default-category visuals, conservative reconverging
paths, pre-existing walk/material limits, matching visual-protocol builds, and
unperformed in-game acceptance. The classifier is not selected-route information.

Package name must include `issue3-turnout-fix-test`, with internal TOML version
still 1.5.0-alpha.1b. Do not modify the original dist/alpha-1b package, include
generated files in Git, merge into main, or close Issue #3 on this evidence alone.

Post-review validation: the final clean build without the build cache passed,
with all 301 assertions. The final JAR was inspected and again compared against
the original Alpha 1b package: 183 existing classes match, and changes are confined
to the visual renderer, visual board index and visual network classes. Original
package hash is unchanged. No further source edits followed this validation.
