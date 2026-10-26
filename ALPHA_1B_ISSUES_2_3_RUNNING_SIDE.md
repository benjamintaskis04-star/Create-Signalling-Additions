# Alpha 1b Issues #2/#3 combined corrective test build

Base: `8cd6f488105c81801b7769be292cd1fbf3551367`, branch
`feature/1.5.0-alpha-1b-visuals`. Internal version remains `1.5.0-alpha.1b`.

## Investigation and change

SpeedZoneSleeperMesh stores a face's signed lateral offset using
`(-tangent.z, 0, tangent.x)`. This points to the right of canonical travel in
Minecraft's coordinates. The old renderer assigned positive offsets to forward
paint slot 0 and negative offsets to reverse slot 1, which is right-hand running.
This conclusion follows from the code, independently of the reported screenshot.

Added the NeoForge CLIENT enum setting:

```toml
[speed_zone_visualisation]
speed_highlight_running_side = "LEFT"
```

File: `config/create_signal_extensions-client.toml`. Allowed values are `LEFT`
and `RIGHT`; LEFT is the schema default, including existing installations missing
the key. The snake_case key matches the surrounding CSA configuration conventions.
NeoForge corrects invalid values to LEFT. Both values use normal TOML persistence.

New SpeedHighlightRunningSide.directionIndex maps each physical half to an
existing direction slot. LEFT assigns the negative canonical side to slot 0;
RIGHT assigns the positive side to slot 0. Reverse travel occupies the other
half. Reversing a graph edge flips its side sign and directional slots together,
preserving the physical lane relative to travel. No camera or fixed compass axis
is involved. Curves, slopes and turnout branches use the existing local tangent.

Only presentation changes: selection returns the entire existing Paint, so its
speed, pulse distance and travel direction stay together. Opposite restrictions
swap physical halves, not operational values. When only one direction has a
known restriction, the existing full-width tint remains; the setting distinguishes
halves when opposing restrictions overlap. Issue #3 route classification and
blue-X20/green-normal ownership remain unchanged.

The setting is read once each render frame, outside the face loop. It is not
cached in geometry and does not need a graph rebuild. Inspected NeoForge 21.1.243
ModConfigSpec.afterReload: it clears values without a restart requirement, which
includes this setting. Once a config UI/save or file reload is applied by
NeoForge, the next rendered frame uses the new mode. File-watcher timing depends
on the client environment; simply editing the file is not proof of a reload.

## Dedicated-server compatibility

This implementation is purely client-side. No operational or visual packet,
registration/protocol version, board index, saved train data, enforcement, X20
calculation, signal or physics source changes are made. Visual protocol remains
Issue #3's version 2; all other payload protocols remain unchanged.

**The Issue #3 corrective JAR is compatible on the dedicated server with this
combined client build.** The server does not need replacing or restarting to
change running side. Clients can use different settings independently. The older
original Alpha 1b server predating Issue #3 still lacks protocol v2 and is not
compatible with either corrective client build; that existing limitation remains.

## Tests and reviews

`runningSideChecks` is added to Gradle check alongside the unchanged Alpha 1a,
Alpha 1b and Issue #3 checks. It tests default/missing/invalid keys, LEFT/RIGHT
TOML round-trips, 16 headings/local curve tangents, rising/flat/falling tracks,
canonical-edge reversal, opposing restrictions, mode changes, pulse ownership
and the unchanged mirrored straight/diverging turnout classification.

Two separate self-review passes are recorded in
`ALPHA_1B_ISSUE_2_REVIEW_1.md` and `ALPHA_1B_ISSUE_2_REVIEW_2.md`. These are by the
implementing agent, not independent reviewers or in-game acceptance reports.

Final `clean build --offline --no-build-cache --console=plain --no-watch-fs`
passed with Java 21, Minecraft 1.21.1, NeoForge 21.1.243 and Create 6.0.10-280.
All 715 assertions passed: 25 Alpha 1a + 221 Alpha 1b + 55 Issue #3 + 414 Issue #2.
All 12 tasks executed. Existing deprecation and terminal-capability warnings
remain; no build errors. No source edits followed this successful build.

Final JAR comparison with the Issue #3-only delivery: 191 existing mod classes
are identical; only SpeedZoneRenderer and ClientConfig differ, with the new
SpeedHighlightRunningSide enum added. Production metadata version and exclusion
of regression test classes were verified.

JAR: `build/libs/create_signalling_additions-1.5.0-alpha.1b-issues2-3-running-side-turnout-test.jar`

Delivery folder: `dist/alpha-1b-issues2-3-running-side-turnout/`, with JAR, these
notes, two review reports and SHA256SUMS.txt.

SHA-256: `c21abbae9b99ac875b14b3cf5113ddd228bf4d0b60d64790ba2fd50b0464bc26`

Preserved Issue #3-only JAR hash:
`e19354c7df28d9d21712c1673c6274c953192ff9a6179d5a77e543748b2335c3`.

## Remaining limitations and acceptance

No interactive Minecraft validation was performed. Issue #2 should remain open
until tested in game. Verify opposite-facing boards with different limits from
both camera directions, with LEFT and RIGHT, across straight, curved, sloped and
reversed graph geometry. Check pulses still move in the restriction direction.
Place ordinary 100 and X20 boards at mirrored turnouts to verify Issue #3 remains
green on main/blue on diverging. Reload the client config while viewing track,
restart the client to check persistence, and join an Issue #3 dedicated server.

Existing whole-sleeper geometry, ambiguous symmetric Y handling, conservative
limits after reconvergence, material support and render budgets are unchanged.
This setting does not supply selected-switch or per-train routing information.

The package classifier is `issues2-3-running-side-turnout-test`. Previous original
Alpha 1b and Issue #3-only packages are preserved in their existing dist folders.
No merge into main, issue closure, or generated artifact commit is performed.
