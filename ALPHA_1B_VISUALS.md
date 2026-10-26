# Alpha 1b visual investigation

Subsequent Issue #3 turnout correction: see `ALPHA_1B_ISSUE_3_TURNOUT_VISUALS.md`.
The original delivery and audit history below is retained for comparison.

Inspected before implementation: Alpha 1a `SpeedZoneRenderer`, the installed
Create 6.0.10-280 slim JAR (models, textures, TrackEdge and TrackRenderer bytecode),
and Create's StationRenderer, TrackBlock and BezierConnection source.

Create's station renderer colours TRACK_ASSEMBLING_OVERLAY using cutoutMipped.
The overlay is separate sleeper geometry with face-specific texture coordinates,
not a repeating transparent sleeper mask. Alpha 1a incorrectly stretched the
entire assembly texture across a ground-level ribbon. Assembly preparation only
rotates straight station track: it cannot directly cover arbitrary graph curves.
Curve rendering instead uses material-specific tie models and baked tieTransform
matrices, independently of the rails.

Implementation constraints established before coding:

- Reuse upward sleeper faces from Create's baked track models and curve tie
  transforms, including their original UVs. Apply translucent, full-bright colour
  just above those faces. Never replace Create resources or render rail models.
- Standard Create sleeper top faces use the `standard_track_mip` sprite, whose
  transparent pixels preserve sleeper gaps. Unknown material layouts must be
  skipped rather than accidentally tinting rails. Resource packs changing that
  convention need visual verification.
- Keep the existing oriented board payload and server authority unchanged.
  Opposite restrictions need independent visual lanes; resolve overlapping
  restrictions conservatively to the lower positive speed in each direction.
- No active train route or turnout-specific speed is supplied to the client.
  Show possible compatible continuations, not a claim about a selected route.
  X20 boards remain excluded from the existing fixed-zone visual index.
- Bound graph work and cached geometry; do not load chunks. Animation must only
  change colour each frame, without rebuilding track geometry.
- In-game appearance, terrain occlusion and FPS require a running client and a
  representative railway. Compilation and deterministic checks alone cannot
  satisfy Issue #1's in-game acceptance gate.

## Delivery and validation

Both visual changes are implemented on `feature/1.5.0-alpha-1b-visuals`.
At the initial delivery, no merge, remote publication or issue closure was performed.

Tint now uses actual upward sleeper faces and original texture coordinates,
including straight/diagonal/ascending baked track models and primary Bezier tie
transforms. Shared physical geometry prevents two restrictions from painting
over each other: opposing directions occupy opposite sleeper halves. Compatible
turnout exits inherit the visual restriction until their next oriented board.

Client TOML: `[speed_zone_visualisation]`, `pulse_enabled` (default true),
`pulse_speed` (default 3.0 blocks/second, range 0.1–20.0), existing `opacity`
(new default 0.5, range unchanged), `show_all_speed_zones` and `render_distance`.
Existing opacity values remain valid; increasing an existing 0.25 setting to 0.5
may improve readability. Pulse brightness is added to a continuously visible base.

Separate self-review reports: ALPHA_1B_REVIEW_1.md and ALPHA_1B_REVIEW_2.md.
Final `clean build --offline --console=plain --no-watch-fs` passed with Java 21,
Minecraft 1.21.1, NeoForge 21.1.243 and Create 6.0.10-280. All 25 existing
regression checks and 221 pulse assertions passed. Four existing NeoForge
deprecation warnings remain; no build errors remain.

Packaged JAR inspected: 697 entries; visual classes, mixin configuration and
Alpha 1b mod metadata present. All 136 content/mixin/network class entries are
byte-identical to the retained successful Alpha 1a build. Resource bundle and
server sources are unchanged.

JAR: `build/libs/create_signalling_additions-1.5.0-alpha.1b.jar`
Delivery copy: `dist/alpha-1b/create_signalling_additions-1.5.0-alpha.1b.jar`
SHA-256: `573a9da3678550bd9e57310e0029b5558074e6dffce848865be5e86da5084272`

### Remaining acceptance gate

No interactive Minecraft visual test was performed in this session. This is an
implementation/test build; Issue #1 must remain open until that validation passes.

- Compare 100 km/h green tint over grass, stone and ballast with pulse disabled.
  Check sleeper texture/gaps and original rail appearance at several camera angles.
- Test both directions on straight, diagonal, curved and ascending tracks,
  slope transitions, crossings, adjacent parallel lines and both turnout exits.
  Use different limits in opposing directions to check independent half lanes.
- Check restricted-to-unrestricted boundaries, coincident boards, loops and
  boundaries on each branch. Boundaries are resolved at whole sleeper-face centres,
  rather than clipping individual sleepers precisely at a board.
- Put solid blocks between camera and track; check for occlusion and z-fighting.
  Compare frame time with overlays hidden/shown and pulse enabled/disabled.
- Change pulse speed and opacity live; check pause, logout/rejoin and dimension
  changes. Drive trains past fixed and X20 boards and signals to confirm unchanged
  server behaviour in both integrated and dedicated-server play.

Unsupported material/resource-pack layouts are skipped if they do not expose
Create's standard sleeper-top sprite. Pulses use graph fractions, so apparent
speed can vary slightly along Bezier curves. No active-route indication or
turnout-specific/X20 visual limit is inferred from the fixed-board payload.
Very large networks can reach the documented render/traversal budgets and show
partial overlays. These limits never affect operational restrictions.

## Git and GitHub audit - 27 October 2026

- Origin already points to the renamed repository:
  https://github.com/benjamintaskis04-star/Create-Signalling-Additions
- After fetching, the development branch and origin/main both started at
  `e7212c8` (public mod-description README). All current main commits are already
  included in the development branch; no integration merge was needed.
- Local README.md matches the main-branch blob
  `a1f72cfb4d879c51f8ff6c4e8a0d03898253eef8`. The local main branch still points
  to `1123dce`; it was left untouched.
- The outstanding Alpha 1b renderer, sleeper mesh, pulse helper, client options,
  checks, build/version changes and these validation/review notes are being
  committed and pushed on `feature/1.5.0-alpha-1b-visuals` only. Generated JARs,
  build outputs, logs and dist files are excluded by the existing ignore rules.
- Existing successful build evidence is recorded above; this Git-only audit
  does not claim a new build or new in-game validation. No source behaviour was
  changed during this audit, including speed enforcement and signalling.
- GitHub Issues #1 and #2 were both OPEN when checked. Issue #1 still needs
  in-game acceptance. Issue #2 additionally needs implementation of its requested
  `speedHighlightRunningSide` LEFT/RIGHT client option (default LEFT): that option
  is absent from the current code. It must not be described as testing-only or
  completed. No issue states were changed.
- No conflicts or unexpected source edits were found. No merge into main,
  branch deletion, force-push or history rewrite is part of this audit.
