# Issue #2 review 2 - configuration, compatibility and package

Second separate self-review, 27 October 2026, following geometry review 1.

Reviewed ClientConfig's enum registration against the existing CLIENT spec
registration, NeoForge 21.1.243 ModConfigSpec reload behaviour, test coverage,
source scope, clean-build log, internal metadata and packaged class comparison.

- Default is explicitly LEFT in defineEnum, not merely the enum's first member.
  Tests check getDefault, an existing config without the key, invalid correction
  and preservation of RIGHT. Both values round-trip through the actual TOML
  writer/parser and spec correction.
- No world/game restart requirement is set. NeoForge afterReload clears such
  cached ConfigValues; the next frame's getter observes the new value. No custom
  watcher, thread or stale geometry cache is introduced. Interactive reload
  remains an in-game acceptance step, not a claimed test result.
- Source diff against 8cd6f48 leaves content, networking, mixins, Issue #3 route
  classifier, sleeper mesh, pulse helper and mod version unchanged. Only client
  presentation/configuration plus test/build/documentation files change.
- Final clean build without build cache passed all 715 assertions: original
  Alpha 1a 25, Alpha 1b 221, Issue #3 55 and Issue #2 414. Twelve tasks executed.
  Existing API deprecation and terminal-capability warnings do not affect results.
- Packaged internal version is still 1.5.0-alpha.1b. Distinct combined artifact
  has classifier issues2-3-running-side-turnout-test; tests are not packaged.
- 191 existing mod classes are byte-identical to the Issue #3 JAR. Only
  SpeedZoneRenderer and ClientConfig changed; SpeedHighlightRunningSide is the
  sole added production class. Network classes and registration/protocol remain
  byte-identical, so the existing Issue #3 dedicated-server JAR is compatible.
  This is static/protocol evidence, not a live multiplayer connection test.
- Original and Issue #3-only JARs were not overwritten. Outputs remain ignored.

No unresolved implementation defect found. No server update/restart is needed
for this client setting. Preserve the documented limitations and keep in-game
acceptance open. This is a separate self-review by the implementing agent, not
an independent reviewer.
