# Issue #3 review 1 - visual data and operational isolation

Dedicated self-review completed 27 October 2026, after the clean build.

Reviewed SpeedZoneBoardData, SpeedZoneSync callers, SpeedBoardBlockEntity's
configuration, SpeedZoneBoardsPayload and both ModNetworking registration paths.
Compared the correction against HEAD and packaged Alpha 1b class files.

Findings and verification:

- Turnout speeds already exist in the board configuration. The index's explicit
  exclusion was the missing visual-data path; removing it does not modify board
  configuration or train state. Link cache keys already include turnout mode,
  speed, block state, target and graph revision, so config edits cause refresh.
- The new flag is included in record equality, visual NBT and the outgoing
  packet. SpeedZoneSync therefore detects a mode change without new sync code.
  Old visual NBT lacking Turnout reads as false. Existing restrictions and train
  persistence formats are untouched. Unrestricted boards cannot be X instructions.
- Codec encode/decode field order agrees, the bounded board-count guard remains,
  and ordinary plus X20 records round-trip in a production-codec regression test.
- Only the visual payload uses protocol v2, in both dedicated-server and client
  registration. Inspected NeoForge 21.1.243 PayloadRegistrar: versioned returns a
  copy, so other operational packets retain v1. Older Alpha 1b clients/servers
  need upgrading together; this is documented instead of allowing bad decoding.
- The packet TYPE now uses the same literal namespace/path without initialising
  the mod's registration class during codec-only tests. Payload ID is unchanged.
- No new client renderer reference is introduced on the dedicated-server path.
- Clean build/check passed with the requested dependency versions; 25 existing
  operational regression checks, 221 existing pulse assertions and 55 new turnout
  assertions passed. The generated TOML still declares 1.5.0-alpha.1b.
- Package comparison found 183 existing mod classes byte-identical. Changed
  classes are limited to SpeedZoneRenderer (including nested types), visual
  SpeedZoneBoardData (including nested types), ModNetworking and the visual codec;
  TurnoutVisualRoutes is new. Enforcement, X20, signals, train physics, actual
  route selection, sleeper geometry and pulse classes remain byte-identical.

Result: no unresolved data/compatibility defect found in this pass. The explicit
client/server visual-protocol compatibility requirement remains a delivery note.
This review is by the implementing agent, not an independent reviewer or live
dedicated-server/in-game validation.
