# Graph exporter — independent review 2

Date: 27 October 2026 (Australia/Sydney).
Scope: thread safety, performance bounds, files, permissions and compatibility.

A different read-only reviewer inspected NeoForge command registration, the exporter, failure paths and focused tests/log. No files changed or builds were run by the reviewer.

Confirmed findings/recommendations corrected:

- Null graph entries no longer abort valid graph capture.
- Attempted dimensions are capped independently of successful JSON writes, so write failures cannot bypass image/dimension bounds.
- Captured graphs receive distinct deterministic colours.
- Operator level 2 is checked at root, leaf and handler, including command-root/leaf merging concerns.

Verified positively:

- Capture occurs on the server thread; immutable DTOs are the only graph/world data passed to the worker.
- Graph/node/edge/sample bounds, fixed rasters, tile limits and one-job gating constrain work/memory.
- Worker failure releases the busy gate; stop cancellation suppresses later output/feedback.
- Cold live curve runtime caches are not initialized, avoiding their length-sized allocation.
- Unique create-new paths preserve previous exports; dimension encoding prevents traversal/collisions.
- Snapshot/renderer/file code introduces no SavedData, restriction, movement, braking, signalling, reservation or network mutation path.
- The manager change is a bounded read-only registry-view overload; prior operational code is unchanged.

The reviewer inspected the **52-assertion** focused run, including JSON-write failures, permissions, immutable/read-only capture, limits, cancellation and file consistency. The primary author added deterministic repeat-JSON checking and executed the final clean suite; those actual results are in [the report](ALPHA_1B_GRAPH_EXPORTER_REPORT.md).

Final outcome: **no remaining confirmed release-blocking issue** in reviewed scope, conditional on final full build and local testing. Live command execution, actual capture latency and dedicated-server headless/filesystem behaviour remain acceptance work. No in-game or multiplayer result is claimed.
