# CSA 1.5.0 Alpha 1 Diag

For the current Alpha 1b graph-association correction and focused diagnostics,
see [ALPHA_1B_GRAPH_ASSOCIATION.md](ALPHA_1B_GRAPH_ASSOCIATION.md).
The Alpha 1 diagnostic-package history below is retained unchanged.

Diagnostic source package based on Alpha 1. Mod version remains `1.5.0-alpha.1` for compatibility; package suffix `Diag` is diagnostic only.

## Config
`config/create_signal_extensions-common.toml` -> `[speed_restrictions] speed_diagnostics = true` (enabled by default in this diagnostic package). Independent of `[signals] signal_debug`.

Logs `[CSA SpeedDiag] LOAD`, `LOADED`, `RESTORE`, `UPDATE`, `REMOVE`, `SAVE`, and `SAVED` with train UUID, posted/route limits, shunt flag, and pending-boundary count. Existing speed-board operational messages also use the independent speed_diagnostics setting. Log output can be large when trains repeatedly change restrictions; disable when finished.

## Testing
Stop the active test train after a 60 km/h board, save and quit, relaunch, then check matching UUID across SAVED, LOADED and RESTORE messages. Repeat with a pending higher-speed board while the rear has not cleared. The unused Handcar may also be listed; ignore it when interpreting the active test train.

Use BUILD.bat as before. This package is source, not a compiled JAR.
