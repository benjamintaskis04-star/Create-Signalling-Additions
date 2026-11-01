# Create: Signalling Additions (CSA) 1.5.0 — Development Roadmap

**Status:** Alpha 1 accepted and tagged (`v1.5.0-alpha.1`); Alpha 2 next  
**Baseline:** CSA 1.4.1; completed 1.5.0 Alpha 1 checkpoint: `v1.5.0-alpha.1`  
**Target platform:** Minecraft 1.21.1, NeoForge 21.1.243, Create 6.0.10, Java 21  
**Build delivery:** Complete Windows-buildable source ZIP with `BUILD.bat`; test JARs may also be supplied when actually built and verified.

## Release sequence

| Stage | Focus | Completion criteria |
|---|---|---|
| **Alpha 1 — complete** | Persistent speed restrictions, speed-zone visualisation and track-graph diagnostic exporter | Accepted local milestone, tagged `v1.5.0-alpha.1`. Includes saved restrictions, pending boundaries and long-train handling; directional sleeper highlighting; `/csa graph export` PNG/JSON diagnostics with slope-boundary elevation labels. Known runtime-test limitations remain documented in the final completion report. |
| **Alpha 2** | Shunting improvements | Preserve shunting through stops and reversals; correct entry and exit conditions; maintain 25 km/h default shunt limit; restore underlying speed restriction on exit. |
| **Alpha 3** | Closest-train priority | Identify closest relevant approaching train to determine displayed aspect; when identification is uncertain, use the most restrictive valid aspect; handle stopped/reversing trains safely. |
| **Alpha 4** | Reservation handling | Interpret each train's authorised route correctly; prevent signalling authority beyond its own clear route; leave Create's reservation management authoritative. |
| **Alpha 5** | Integrated signal housing and Rail Signalling Enclosures | Investigate hosting Create signal functionality inside compatible CSA signals with automatic linking and no separately visible Create signal block; introduce a trackside enclosure for multiple Create signals and CSA Route Interfaces. Preserve individual linking-tool targets, Create authority, graph associations and persistence. Detailed implementation and interaction design are deferred. |
| **Alpha 6** | Configuration and performance | Make operational limits configurable with existing defaults; minimise redundant per-tick scans, recalculations, allocations, chunk loading and saves; preserve signalling correctness. |
| **Alpha 7** | Low Speed signalling | Implement Low Speed aspect and behaviour; initially reuse the existing Shunt Add-on model with a green lamp, while main lights show Red over Red; integrate relevant speed controls. |
| **Alpha 8** | Railway map integration | Track linked CSA signals at their authoritative Create track-graph positions; display live aspects by default; offer optional detailed route/reservation/train information via client config; keep external map integrations optional and assess supported APIs. |
| **Beta 1** | Multiplayer/server testing | Begin server testing, compatibility checks and profiling after locally tested alphas. |

## Agreed design principles

- Each alpha includes the features from preceding alphas; avoid introducing later-stage features prematurely.
- **Alpha builds are tested locally only.** Server/multiplayer testing starts at beta.
- Signal and reservation safety take priority over performance shortcuts. If approaching-train priority is uncertain, choose the most restrictive valid indication.
- Restore saved speed restrictions exactly on reload, while still allowing new, more restrictive instructions to apply.
- Keep existing defaults when adding configurable operational limits; server remains authoritative for operational rules.
- Avoid unnecessary bloat, repeated whole-network scans, forced chunk loads, excessive allocations and redundant persistence writes.
- Use Create's signalling and reservation systems as the foundation rather than replacing them.
- Alpha 1's on-demand `/csa graph export` PNG/JSON diagnostic is distinct from the live railway map integration planned for Alpha 8. For future map support, live aspects are the default and detailed signalling information is an opt-in client setting.
- **Alpha 5 design is intentionally deferred:** the Rail Signalling Enclosure normally uses a closed exterior model supplied by the user. A contextual placement highlight is envisaged when holding an eligible block and aiming at the cabinet, inspired by Create Electro Energetics' Electrical Panel; the CSA Signal Linking Tool should continue to select/link individual hosted components normally. Cabinet capacity, internal rendering, virtualisation versus physical backing, addon support and exact interactions must be investigated at Alpha 5, not fixed prematurely.

## Packaging and correction conventions

1. Prepare a complete source package for the stage, with Java source, resources, build configuration and a Windows `BUILD.bat`.
2. Perform **two reviews** of each package before delivery; correct issues discovered.
3. State clearly whether compilation was actually verified. A ZIP integrity check is not a successful Java build.
4. For an initial alpha that fails to compile, name the first repair **Alpha N Fix**. After that Fix builds, use **Alpha N-1**, **Alpha N-2**, etc. for later implementation corrections.
5. Preserve original Blockbench-exported geometry, UVs, texture assignments, rotations and proportions unless an agreed change is necessary.
6. Keep the CSA Signal Linking Tool out of the `create_signal_extensions:heightvariable` item tag.

## Immediate next milestone: Alpha 2

- Start from the accepted Alpha 1 tag `v1.5.0-alpha.1` on a new development branch; do not rewrite the tagged milestone or merge unfinished 1.5.0 work into `main`.
- Preserve the 25 km/h default shunting restriction across stops and reversals.
- Correct shunt-mode entry/exit and restore the underlying speed restriction on exit.
- Test both manual shunt-keybind operation and shunt-only signal behaviour, including track-graph recognition of end-shunt signs.
- Preserve existing Alpha 1 behaviour, saved-data compatibility and local-only testing policy.

## Progress log

- **2026-11-01:** Roadmap revised after user acceptance of Alpha 1. Added Alpha 5 integrated signal housing and Rail Signalling Enclosures; moved configuration/performance to Alpha 6, Low Speed to Alpha 7, and live railway map integration to Alpha 8. Enclosure interaction details deliberately deferred until implementation.

- **2026-10-27:** Alpha 1 accepted as a local-development milestone and consolidated into `1.5.0-alpha.1`. Final Java 21 build reported 1,027 passing assertions, source-ZIP rebuild produced a byte-identical JAR, and two independent reviews passed. Commit `17a21bc22991c9a090273f8d706b6f888c32358b` and annotated tag `v1.5.0-alpha.1` preserve the milestone. This is not dedicated-server or multiplayer acceptance; see `ALPHA_1_FINAL_COMPLETION_REPORT.md`.

- **2026-10-22:** Alpha 1a integrates the checkpointed local speed-zone overlay, corrects reverse-direction route selection and automatic braking, and completes pending-boundary/deleted-train persistence handling. `BUILD.bat` compilation and deterministic regression checks are verified; in-game acceptance is pending. See `ALPHA_1A_RELEASE_NOTES.md`. This track inspection overlay is separate from the railway map integrations scheduled for Alpha 7.

- **2026-10-21:** Roadmap approved. Previous Alpha 1 WIP/checkpoints are **not** considered completed or compilation-verified releases.

_Update this document as each alpha is built, tested and accepted._
