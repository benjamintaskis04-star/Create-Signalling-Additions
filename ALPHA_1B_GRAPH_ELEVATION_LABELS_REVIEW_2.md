# Elevation-label correction — independent review 2

Date: 27 October 2026. Scope: presentation-only changes, performance, layer visibility, compatibility and regressions. Independent reviewer: `elevation_review_safety`; this record summarizes its findings. Review was read-only.

## Findings and resolution

1. **Opaque label boxes could hide speed-board/node markers.** Annotation drawing moved before the unchanged marker layers. A real-render pixel regression now verifies a physical board marker remains visible even inside a label box. Resolved.
2. **Crowded unsuccessful placement could try many candidates.** Existing graph/image limits already bounded the work; a separate **512 visible candidate attempt** limit was added alongside the 64-label ceiling and fixed per-candidate search. A focused test checks exhaustion before a later placeable label. Resolved.
3. **Explicit flat-overpass coverage needed.** Added both no-label checks and actual JSON checks retaining exact elevations/connectivity for independent flat tracks crossing in projection. Passed.

The source diff and baseline hashes confirm only `GraphExportFiles.java` and `build.gradle` changed among pre-existing files. New annotation code uses immutable captured values and Java2D/collections only. No live graph/server access, operational writes, networking or new per-tick processing was introduced. JSON writing, sampling, capture, associations and command handling are unchanged. The prior exporter JAR retains its original SHA-256.

Node comparisons are capped at 16 incident edges; labels at unknown, displaced or insufficient geometry remain omitted. PNG placement stays within the map and avoids intersecting text boxes. All work remains on the existing export worker.

## Outcome

**No remaining findings or blockers.** The reviewer inspected the final executed build log: Java 21 `BUILD SUCCESSFUL in 41s`, all 17 tasks executed, **1,025 assertions passed including 43 new checks**. The primary agent executed the build; the reviewer did not run builds or edit files.

Documented limitations remain: dense images can omit labels; uncertain geometry is not guessed; flat overpasses intentionally have no Y labels, retaining their elevations in JSON and existing styling. In-game acceptance remains outstanding.
