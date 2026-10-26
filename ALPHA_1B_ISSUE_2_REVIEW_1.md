# Issue #2 review 1 - travel-relative geometry and rendering

Dedicated self-review pass, 27 October 2026.

Reviewed the existing mesh's side calculation, canonical primary curve handling,
Piece.forward slots, per-face Paint ownership, new enum and renderer diff.

- Positive mesh lateral is canonical travel's right; old slot selection therefore
  represented RIGHT. LEFT reverses just that selection. This is independent of
  the reported screenshot and does not rely on camera facing.
- With reversed canonical travel, the same physical face has opposite side sign
  and the same restriction moves to the opposite slot. Tests derive physical
  left from UP cross TRAVEL independently of the production index helper.
- Curves use local tangents, slopes retain horizontal lateral orientation, and
  primary curve caching is untouched. No model/UV/normal/rail/depth changes.
- When both directional owners exist the selected Paint moves as a whole:
  speed, pulse phase and travel orientation remain attached. Single-direction
  full-width rendering is retained. The enum does not touch path traversal.
- Issue #3 classifier, waiting X instructions, boundaries and normal/diverging
  speed overlays are unchanged. Mirrored turnout tests exercise both modes.
- Config is read once per render frame, adding no per-face allocations or mesh
  rebuild. Only a mode selection replaces the previous hard-coded sign test.

No unresolved direction-assignment defect found. Mathematical regression tests
are not a replacement for testing track textures and half-lane appearance in game.
This is a self-review, not an independent reviewer.
