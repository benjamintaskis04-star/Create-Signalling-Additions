# Alpha 1b corrective test build - cosmetic X boards

Base: `64d9b066d2e031848cc423c7601554edc8a225ca`.
Branch: `feature/1.5.0-alpha-1b-visuals`. Version: `1.5.0-alpha.1b`.
This change supersedes Issue #3's special X/turnout semantics. It retains the
Issue #2 LEFT/RIGHT setting and all ordinary speed-board behaviour.

## Investigation and implementation

The prior SpeedRestrictionManager had three special X paths: front passage could
be ignored or armed pending a junction; upcoming braking skipped X boards unless
the next junction was classified diverging; manual steering could subsequently
activate an armed X limit. These paths, their selector/classifier and transient
pending fields are removed. Every board now enters the ordinary category/manual
speed selection, directional passage, front application of lower limits and
rear-clearance application of higher/unrestricted limits. The numeric braking
envelope, train physics, shunt/signalling logic and actual navigation are unchanged.

SpeedBoardRules exposes the existing higher-limit and direction predicates for
direct regression checks; its inputs deliberately have no X flag. The copied
navigation selector is unchanged apart from package visibility for tests.

The ordinary advance scout still follows Create's copied actual navigation path
or existing steering selector. SpeedBoardRouteMatch additionally rejects nearby
boards linked to a different scouted edge: a diverging board immediately beyond a
fork can be within the old radius on the main path. Curve links match connection
endpoints in either orientation; straight links match the centreline (including
the existing block-centre height offset). The cheap distance test runs first.
This read-only eligibility filter applies equally to ordinary and X boards,
introduces no braking formula or turnout selection, and never loads chunks.

SpeedZoneRenderer now indexes ALL boards as ordinary boundaries and starts each
zone at its own board position, regardless of the legacy flag. Removed its
waiting-X state and separate diverging overlay, and deleted TurnoutVisualRoutes.
SpeedZoneBoundaryResolver likewise accepts X boundaries. Sleeper mesh, texture
coordinates, rails, pulse code and running-side config remain unchanged.

## Saved worlds, GUI and placement

SpeedBoardBlockEntity, its GUI, placement/linking code, sign renderer, static
resource bundle and configuration packets are unchanged. The X toggle and all
default/MU/high-speed settings retain their existing NBT keys and displayed
textures. Existing X boards work without replacing/reconfiguring them. Tests
save and restore the real board implementation with all panels and the X bit.
The GUI's legacy turnout wording is deliberately left exactly as supplied.

No board is moved or relinked automatically. An old X20 on the common approach
now behaves like an ordinary 20 there. For a diverging-only limit, place/link it
on that branch immediately after the turnout begins. There is no automatic
turnout detection or new placement restriction.

Existing per-train SavedData is unchanged. Previously active legacy route caps
remain as an already-active limit across reload; the next ordinary OR X board
supersedes them via the same front/rear-clearance rules. This avoids abruptly
releasing a saved active restriction. No new route caps or manual X arming are
created. Old pending manual arming was transient and is no longer implemented.

## Networking and rollout

No codec, payload registration, protocol version or operational persistence
schema changes are needed. The legacy visual Turnout boolean still round-trips
as compatible metadata, but the new renderer ignores it. The visual board index
and sign configuration retain the bit; the sign renderer alone uses it as an X
prefix. This avoids rewriting existing sign data.

**Update BOTH client and server for consistent new behaviour.** The server update
removes X-specific enforcement; the client update removes the old special
overlay. Older corrective versions can still connect on the unchanged wire
protocol, but mixed versions do not implement the same semantics. The unchanged
internal version does not automatically detect that mismatch. Update the server
normally with a backup; unlike Issue #2, this is an operational rule change.

## Regression coverage and limitations

- Retained Alpha 1a rear-clearance, braking and geometry checks, and Alpha 1b
  palette/pulse checks. Updated the obsolete expectation that X is not a boundary.
- Updated former Issue #3 checks to assert X20/20 and X40/40 boundary parity and
  retained visual packet round-trip/count checks. Removed obsolete branch-classifier
  expectations from the running-side suite; lane/TOML checks remain.
- CosmeticXChecks exercises real board configuration and NBT write/read with a
  headless fixture (panel availability/block-state validation overridden, no world),
  ordinary/X category/manual equality, both sign directions, front/rear predicates
  and the existing envelope with the actual selected speeds.
- Uses actual Create TrackGraph/TrackEdge/TravellingPoint objects with an in-memory
  adjacency map and production copied-path selector: tests main versus diverging
  paths, reversed point travel, an immediate branch board and unmodified original
  navigation data. Curve-link tests cover primary/reverse connections and reject
  neighbouring curves. These are headless graph tests, not a live railway test.

Interactive placement, dedicated-server rollout, long train crossings, manual
steering changes and in-game appearance still need acceptance testing. The front
trigger retains its pre-existing proximity/direction logic; the new edge filter
is specifically for advance scouting. Existing loaded-chunk, render-distance,
material/whole-sleeper limits and conservative possible-path overlays remain.
No selected route is inferred for the static overlay; branch limits come from
boards actually linked to that branch. No merge into main or issue closure.

Separate self-reviews: `ALPHA_1B_COSMETIC_X_REVIEW_1.md` and
`ALPHA_1B_COSMETIC_X_REVIEW_2.md` (implementing agent, not independent reviewers).

## Final validation and artifact

`clean build --offline --no-build-cache --console=plain --no-watch-fs` succeeded
after the review improvement, on Java 21 / Minecraft 1.21.1 / NeoForge 21.1.243 /
Create 6.0.10-280. All 13 tasks executed; all 806 assertions passed (25 Alpha 1a,
221 Alpha 1b, 404 running-side, 12 boundary/packet, 144 cosmetic-X/route). Existing
deprecation and terminal-capability warnings remain; no compilation errors.

The count differs from the former 715 because obsolete X route-classification
expectations were removed and replaced by the new parity/actual-route checks.

JAR: `build/libs/create_signalling_additions-1.5.0-alpha.1b-cosmetic-x-boards-test.jar`

Delivery copy and SHA256SUMS.txt: `dist/alpha-1b-cosmetic-x-boards/`, with these
notes and both review reports. Previous corrective packages are preserved.

SHA-256: `d700ceaf48e1ca54deb299a5cafb14117a0c8466af979964452d2b45ea839cb9`

Packaged metadata confirms unchanged internal version. No regression classes or
obsolete TurnoutVisualRoutes are packaged. Compared to the 64d9b06 JAR, 184
existing mod classes remain byte-identical; changed classes are restricted to
the manager, visual renderer and boundary resolver (including nested types),
with two new speed-board rule/matching helpers. GUI, sign settings/rendering,
network, saved train data, braking formula, running-side and sleeper/pulse
classes match. No generated JARs, outputs or logs are included in Git.
