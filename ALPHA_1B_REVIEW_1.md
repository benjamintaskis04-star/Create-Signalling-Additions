# Alpha 1b code review 1 — geometry and behaviour isolation

Completed as a dedicated self-review before packaging, 22 October 2026.
This is not an independent reviewer or an in-game acceptance report.

Scope: Alpha 1a diff, Create 6.0.10-280 assembly models and renderer bytecode,
SpeedZoneSleeperMesh, render submission, configuration and animation.

Findings corrected:

- Flat graph coordinates have zero elevation: sampling below them selected the
  support block. Sample just above the graph instead.
- Smoothed ascending transitions require the baked model's world ModelData.
  Forward Create's model data to getQuads, retaining its tilt transform.
- Opposite curve edges must reuse the primary connection's baked sleeper
  transforms, rather than baking the secondary curve independently.
- The initial renderer source had a UTF-8 BOM; removed after javac rejected it.

Verified in code: only upward standard_track_mip faces are submitted; original
positions, UVs and transparent sleeper gaps remain, lifted 1/512 block along
their face normal. Rails use separate sprites/models and are excluded. Atlas
translucency uses normal depth testing; there is no through-terrain render state.
Pulse changes colour/opacity above a continuously visible base tint. Pose stack
restoration is protected by finally. No static resource bundle changes needed.

Server enforcement, X20 braking, SavedData, signal logic, payload definitions and
server sync remain byte-for-byte unchanged in the Git diff. Palette is unchanged.
Initial compile/check succeeded: 25 existing regression checks and the pulse
suite passed. Existing deprecated API warnings remain unrelated to this change.

Limit: source review cannot establish grass contrast, exact visual match,
occlusion with shader packs, or FPS. Those remain in-game acceptance checks.
