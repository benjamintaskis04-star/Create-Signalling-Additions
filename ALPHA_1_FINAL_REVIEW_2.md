# Final Alpha 1 review 2 — package, diagnostics and reproducibility

Date: 27 October 2026. Independent reviewer: `alpha1_final_package`. Read-only review; record prepared by the primary agent from the reviewer's findings.

## Scope and evidence

The reviewer verified all **673 preliminary source-ZIP entries** against both the workspace and isolated extraction, and confirmed all **672 accepted-baseline paths** were included. Source, resources/templates, all nine test suites, `BUILD.bat`, build configuration, licence, static resource bundle, roadmap/history and current usage instructions were present, with no duplicate ZIP names or generated JAR/classes/log/cache/Git/build/dist content.

The static bundle retains **467 entries**, enables `TrainBoundaryMixin` and excludes the Signal Linking Tool from `heightvariable`. Platform pins are unchanged. Every regression task remains attached to `check`. The final report plus two review records bring the final source archive to **676 entries**.

All **48 previous delivery files** retained their recorded hashes. The review confirmed no new operational scans, persistence writes, chunk loads or diagnostic streams; existing export/capture/annotation bounds remain intact. Disabling the diagnostic-build default affects logging only and preserves explicit values.

## Finding and correction

The initial JAR configuration did not explicitly normalize file timestamps/order, allowing whole-file hashes to differ after extraction despite identical compiled contents. This was a reproducibility observation, not an operational defect.

Added Gradle's standard `preserveFileTimestamps = false` and `reproducibleFileOrder = true`, then reran the clean build. An actual isolated source-ZIP `BUILD.bat` build produced a JAR with the **same SHA-256 as the workspace JAR**. No runtime code was redesigned to address packaging.

## Validation and outcome

The primary agent executed the Java 21 clean build: **47 seconds, 17 tasks executed, 1,027 assertions passed**. It also executed the ZIP's own `BUILD.bat`: **52 seconds, all 1,027 assertions passed**, producing the exact intended `create_signalling_additions-1.5.0-alpha.1.jar`. Logs and preservation/completeness evidence were supplied for independent inspection. The final archive is separately extracted and built again before delivery.

**No remaining confirmed source/package, performance, diagnostic or reproducibility blocker.** Existing caches/distribution were reused for validation; fresh online bootstrap and a clean dependency cache were not tested. No claim of dedicated-server, multiplayer or cross-toolchain reproducibility is made. The reviewer did not edit files or run builds.
