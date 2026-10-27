# Cosmetic X review 1 - operational rules and save compatibility

Dedicated self-review, 27 October 2026.

Reviewed the full SpeedRestrictionManager diff against 64d9b06 and all references
to isTurnoutBoard, arming, route classification and legacy persisted caps.

- All prefix-specific front/advance/manual paths are removed. The ordinary path
  still selects category/manual limits, direction, lower/equal front application,
  pending higher boundaries and rear clearance. New predicate helpers contain
  the same formulas/deadbands, with no prefix input.
- Braking formula/look-ahead bounds and physics methods are unchanged. The
  actual-route copied selector body is unchanged and tested with real Create
  travelling points in both point orientations, without consuming the original
  path. The manual ordinary scout still uses the existing steering selector.
- New edge eligibility addresses a near-fork radius false positive equally for
  X and ordinary. It is read-only and uses no train controls or chunk requests.
  Review improvement: move it AFTER the cheap proximity check to avoid evaluating
  geometry for every far-away board at every scout step. Rebuild after this edit.
- Real configuration/NBT regression confirms the old X bit and all speed panels
  survive. An initial headless fixture lacked a block type; its test-only state
  validation now bypasses registration without changing production board code.
- Legacy persisted route caps are retained only for continuity, with existing
  rear-boundary serial handling. No new route limit or X arming is created. A
  loaded old X restriction is superseded exactly as an existing ordinary limit.
- GUI, board placement, sign renderer/resources, persistence and protocol sources
  are unchanged. Old approach placement now means an ordinary approach restriction;
  branch-only placement requires the existing explicit track linking workflow.

No unresolved operational defect found in this pass. Live trains, manual route
changes and saved-world upgrades still need in-game acceptance. This review is
by the implementing agent, not an independent reviewer.
