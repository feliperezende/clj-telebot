# AGENTS.md — clj-telebot

Compact context for OpenCode sessions.

## Developer commands

```bash
lein check        # compile check
lein test         # run all tests
lein run          # start bot (requires TELEGRAM_BOT_TOKEN)
lein uberjar      # creates two JARs; deploy with *-standalone.jar
```

`TELEGRAM_BOT_TOKEN` is loaded lazily on first Telegram API use (`delay` in `api.clj`), so `lein check` / `lein test` do not need a real token at namespace-load time. CI still sets `TELEGRAM_BOT_TOKEN=dummy`. `lein run` needs a valid token.

## Deployment requirements

- `TELEGRAM_BOT_TOKEN` environment variable.
- `yt-dlp` on system PATH (external dependency, not a Clojure dep).
- `ffmpeg` on system PATH (yt-dlp uses it to merge separate audio + video streams).
- Use `target/uberjar/clj-telebot-*-standalone.jar` for deployment (bundles all Clojure deps).

## Update flow

```
Telegram Bot API
       │
       ▼ HTTP GET /getUpdates (long poll)
telegram/api.clj:long-poll-updates
       │  └─ loops: safe-poll → poll-once → run! handler over each update
       ▼ update map
core.clj:process-update
       ├─ rate-limiter/check-limit ──► send rate-limit message (if limited)
       ├─ /download or /dl  ──► core.clj:handle-download-command
       ├─ plain video URL   ──► core.clj:handle-download-command
       ├─ /uptime           ──► core.clj:handle-uptime-command
       └─ other text        ──► core.clj:echo-update
```

## Module roles

| File | Role |
|------|------|
| `core.clj` | Entry point. Extracts chat/text from updates, rate-limits, routes commands and URLs to handlers. Owns `download-pool`. |
| `telegram/api.clj` | HTTP client for Telegram API. Long-polling loop, send message/video, bot command management. |
| `services/video_downloader.clj` | yt-dlp process wrapper. Temp file management under `/tmp/clj-telebot/downloads`. |
| `services/rate_limiter.clj` | Per-chat token bucket. Env-configured capacity, refill rate, and idle-bucket eviction. |
| `misc/helpers.clj` | `required-env` env-var helper. |

## Code conventions

- Docstrings on every public function.
- Type hints required for Java interop (reflection warnings are treated as errors).
- Private functions use `defn-`.
- Tests access private vars via `#'namespace/var-name` reader syntax.
- Result-map convention in downloader: returns `{:success true, :file <java.io.File>}` or `{:success false, :error <string>}`.
- **Every PR must bump the version in `project.clj`.** The CI pipeline builds a release from master and uses the project version for the jar filename and release notes. Bump the minor version for feature additions, patch for bug fixes. Remove the `-SNAPSHOT` suffix on release (e.g., `0.2.0` → `0.3.0`).

## Testing

- `test/clj_telebot/core_test.clj` — pure function tests (message extraction, URL detection, uptime formatting).
- `test/clj_telebot/services/video_downloader_test.clj` — URL parsing tests only (no live downloads).
- `test/clj_telebot/services/rate_limiter_test.clj` — token-bucket allow/deny/refill/eviction tests.
- `test/clj_telebot/telegram/api_test.clj` — API URL building tests.
- `test/clj_telebot/misc/helpers_test.clj` — env var tests.

## Known quirks and issues

- **Bounded download pool.** `handle-download-command` sends an immediate “⏳ Downloading…” message via `send-message`, then submits the work to `download-pool` (`ThreadPoolExecutor`, max 3 threads, `AbortPolicy`). When the pool is saturated the request is rejected with a busy message.
- **Debug `println` in production.** `services/video_downloader.clj` still emits `[DEBUG]` log lines during normal operation.
- **Socket timeout buffer.** `get-updates` sets Hato `:socket-timeout` to `(+ 5000 (* 1000 effective-timeout))` — gives Telegram 5 extra seconds beyond the long-poll timeout.
- **Misleading function name.** `download-twitter-video` is not Twitter-specific; it is a generic yt-dlp wrapper supporting 1000+ sites.
- **Dead code.** `api.clj` contains unused `test1` and `test2` functions (vector-iteration experiments).
- **yt-dlp file naming quirks.** The downloader must search the temp directory after yt-dlp finishes because it creates intermediate streams (e.g., `uuid.fhls-2777.mp4`, `uuid.fhls-audio-XXXX.mp4`) before merging into the final `uuid.mp4`.
