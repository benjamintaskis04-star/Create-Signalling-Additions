# Alpha 1b code review 2 — direction, bounded work and release preparation

Completed as a second, separate self-review before packaging, 22 October 2026.
This is not an independent reviewer or an in-game acceptance report.

Scope: full updated renderer, graph boundaries and traversal, opposite-direction
overlap, client lifetime, tests, resource delivery and version/build entry points.

Findings corrected:

- Overlap resolution and normal calculation originally repeated each frame.
  Resolve directional owners once during mesh rebuild and cache face normals.
- Long straight graph edges could scan far outside render distance. Restrict
  sampling to a camera-centred interval before looking up world blocks.
- A face-assignment limit alone did not bound unused cached mesh geometry.
  Cap cached faces separately and enforce the limit inside mesh extraction.

Verified in code:

- Physical edge geometry is shared between both oriented restrictions. At most
  one paint is submitted per physical top face; opposing restrictions use
  opposite sleeper halves, with independent pulse distances and colours.
- Same-direction overlap chooses the lower positive speed. Unrestricted boards
  terminate preceding zones; coincident-board handling is retained from 1a.
- At turnouts traversal visits compatible forward exits, stopping at each next
  same-direction boundary. Per-path visited edges and per-zone expansion prevent
  loops; phase distance carries into the continuations. No active-route data is
  invented or sent across the network.
- Limits: 2048 blocks and 256 walk steps per starting zone, 4096 walk steps per
  rebuild, 32768 cached faces, 65536 assignments. Extreme networks can therefore
  show partial overlays. Loaded-chunk and render-distance checks remain in use.
- Client config retains show-all, inspection, distance and opacity. Added pulse
  enable and speed settings. New opacity default is 0.5; existing saved values
  remain valid. Alpha 1b version is set in Gradle and BUILD.bat.
- Dedicated server code and packet codecs are unchanged. New rendering classes
  are reached through the existing client-only renderer.
- Deterministic pulse checks cover positive/reverse travel, speed, wrapping,
  base palette preservation and bounded brightness. Existing 1a checks still run.

Residual limits: standard Create material layout only; full sleeper-face boundary
resolution; approximate constant-speed pulse spacing on Bezier curves; possible
continuations rather than selected routes; X20/turnout-specific limits remain
outside the fixed-zone visual index. A live railway is needed to validate
geometry, bidirectionality, grass/stone contrast, occlusion and frame time.

Packaging gate: run clean build/check after both reviews; inspect packaged class
entries and generated version metadata, compare server classes to the retained Alpha 1a build,
then copy the verified JAR and a SHA-256 to dist. Do not merge or close Issue #1.
