# Create: Signalling Additions 1.5.0 — Alpha 1

The user accepted Alpha 1 as a completed **local-development milestone** on 27 October 2026. This consolidated source includes the accepted Alpha 1b boundary-lifecycle corrections, persistent restrictions, route-specific graph association, sleeper visualisation and graph exporter with slope-boundary elevation labels. Internal version: **1.5.0-alpha.1**.

This is not a multiplayer/server release. **Beta 1** remains the planned beginning of server testing. Historical Alpha 1a/1b audits and reports are preserved as records of their respective builds; their earlier pending-acceptance statements do not supersede the user's current milestone acceptance. The final completion report and two final reviews describe this consolidation.

## Build on Windows

Install **JDK 21**, make it available through `JAVA_HOME`/`PATH`, extract the complete source ZIP into a writable local folder, and run **`BUILD.bat`**. The script bootstraps Gradle **8.10**, copies the build inputs to a short temporary directory to avoid OneDrive file-read issues, runs a clean build and all regression checks, and copies the JAR to:

`build/libs/create_signalling_additions-1.5.0-alpha.1.jar`

The build workspace is retained and printed by the script. On the first build, internet access is required to download Gradle and dependencies; those caches are not shipped in the source ZIP. Use a local extracted folder rather than running directly inside the ZIP. `build-support/static-resources.zip` is a required source input, not a generated delivery artifact: the Windows resource-processing task expands it and adds generated mod metadata. Individual source resources are included as well.

Platform pins remain Minecraft **1.21.1**, NeoForge **21.1.243**, Create **6.0.10-280** (Create 6.0.10), Java **21**. Optional explicit Gradle invocation with an installed Gradle 8.10:

```powershell
$env:JAVA_HOME='C:/Program Files/Java/jdk-21'
gradle clean build --console=plain --no-build-cache --no-watch-fs
```

JAR entry timestamps and ordering are normalized for reproducibility. This does not bundle dependencies or guarantee a fresh machine can build offline.

## Retained diagnostics and client setting

- Operator permission level 2: **`/csa graph export`**. Exports are written beneath `<world>/csa-graph-exports/<UTC timestamp>/dim-<encoded dimension>/`, with PNGs, matching `graph.json` and a root `SUMMARY.txt`.
- PNGs retain authoritative available line/Bezier geometry, graph/node/junction markers, board attachments, unresolved boards, coordinate references and elevation-dependent styling. Y annotations mark verified slope endpoints/transitions and interior crests/troughs; continuous slopes and level track do not receive repeated labels. Full XYZ/topology remains in JSON.
- `[speed_restrictions] speed_diagnostics` now defaults **false**. It controls logging only. Existing explicit `true` values remain enabled; set false manually if you want to silence an existing diagnostic configuration.
- `[speed_restrictions] graph_diagnostics` remains **false** by default and rate-limited when enabled. `[signals] signal_debug` remains opt-in. No configuration keys were renamed.
- Client **`speedHighlightRunningSide`** remains **LEFT** by default with RIGHT available. Directional sleeper lanes and pulses are preserved.

No saved-data migration is required for the accepted Alpha 1b source. Active restrictions and pending boundaries retain their existing format, including optional graph anchors and legacy records without anchors. The consolidation does not change enforcement, braking, signalling, movement, packets or sign configuration. Rolling back to an implementation predating the occupied-section correction while pending boundaries exist remains unsafe; that pre-existing limitation is not a migration introduced by this version change.

The final completion report records executed build/test results and remaining runtime scenarios. User milestone acceptance is not evidence that the coding agent observed every in-game scenario. No multiplayer validation is claimed.
