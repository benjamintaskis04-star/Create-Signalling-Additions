# Cosmetic X review 2 - visual boundaries, compatibility and final package

Second separate self-review, 27 October 2026, after operational review 1.

Reviewed the complete visual diff, ordinary-boundary map, outgoing traversal,
updated tests, unchanged GUI/resources/network sources and final JAR comparison.

- All visual boards populate the same boundary map; the old turnout bit no
  longer separates starts or changes traversal. No waiting-X state, branch-only
  overlay or TurnoutVisualRoutes classifier remains in production or package.
- Actual branch boards terminate preceding ordinary colour at their linked
  position, start their own 20/40 zone and follow ordinary continuations. An X
  board is neither retroactively applied before its location nor moved to a
  guessed branch. Coincident-boundary and unrestricted rules remain unchanged.
- Canonical sleeper geometry, palette, pulse phase/ownership, physical inspection,
  LEFT/RIGHT configuration and opposite-direction assignment are retained.
- Board GUI, renderer/models, placement, configuration/saved-world data and every
  payload/protocol are unchanged. Old flags are preserved as cosmetic metadata.
  Both ends still need the new implementation for consistent behaviour, despite
  wire compatibility with previous corrective builds. This is documented.
- Updated obsolete tests rather than retaining assertions for the removed
  classifier. Tests exercise actual board configuration/NBT, Create travelling
  points on both copied routes and reversed point orientations, curve-link
  identity, existing braking envelope, boundary parity and packet compatibility.
- After review 1's performance adjustment, final clean build without build cache
  passed all 806 assertions: Alpha 1a 25, Alpha 1b 221, running side 404,
  boundary/packet 12, cosmetic-X operational/route 144. All 13 tasks executed.
- Final package internal version is 1.5.0-alpha.1b, with cosmetic-x-boards-test
  classifier and no regression classes or obsolete classifier. Comparison to the
  64d9b06 package found 184 existing mod classes identical. Changes are limited
  to SpeedRestrictionManager, SpeedZoneRenderer and SpeedZoneBoundaryResolver
  (plus their nested classes); two small production helpers are new.
- Sign configuration, GUI, sign rendering, networking, saved train data,
  signalling, braking formula, sleeper/pulse and running-side classes match the
  baseline byte-for-byte. Physics and static resources have no source changes.

No unresolved implementation issue found in this pass. Known live-game and
front-proximity limitations are documented; headless tests do not establish
in-game acceptance. No source changes followed the final successful build.
This is a separate self-review by the implementing agent, not an independent
reviewer or multiplayer rollout test.
