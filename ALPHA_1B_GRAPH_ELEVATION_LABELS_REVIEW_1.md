# Elevation-label correction — independent review 1

Date: 27 October 2026. Scope: geometric correctness, coordinates, captured edge geometry and diagnostic fidelity. Independent reviewer: `elevation_review_geometry`; this record summarizes its findings. Review was read-only.

## Findings and resolution

1. **Leader lines could strike through previously painted label text.** The initial single-pass leader/box loop was replaced with separate passes: all leaders, then all boxes/text, then node/board markers. Resolved.
2. **Missing reversed-cubic regression.** Added a cubic with both endpoint and control order reversed; the exact world-coordinate slope-boundary set must match the original. Passed.
3. **New crowded-placement fixture could not fit even its intended short label.** The reviewer identified the mis-centred fixture after the first expanded build stopped at that assertion. The test now derives its width and anchor from actual font metrics. Passed in the final clean build; no production cap defect was found.

Verified outward-gradient comparisons across edge orientation, continuous and eased curved slopes, analytic simple `dy/dt` roots for crests/troughs, no extra labels at double roots, flat-track suppression, shared-node deduplication and conservative unknown/portal/mismatched-endpoint handling. Distinct graphs and different elevations remain separate. Continuously varying gradient within a cubic is intentionally one slope unless it reverses vertically.

The final generated PNG was visually inspected: slope endpoint/crest labels were legible, nearby elevation labels separated, flat overpasses had no repeated elevation text and projected crossings remained unconnected. JSON writing, capture, associations and elevation stroke logic were unchanged in the reviewed source.

## Outcome

**No remaining confirmed blocker.** The reviewer inspected the final executed build log: Java 21 `BUILD SUCCESSFUL in 41s`, 17 tasks executed, **1,025 assertions passed including 43 new checks**. The primary agent executed the build; the reviewer did not execute it independently.

Dense maps may conservatively omit labels under documented geometry and layout limits. No live-world or in-game acceptance was performed or claimed.
