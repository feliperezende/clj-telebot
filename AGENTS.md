# AGENTS.md — clj-telebot

Compact context for OpenCode sessions.

## Developer commands

```bash
lein check        # compile check (requires TELEGRAM_BOT_TOKEN — see below)
lein test         # run all tests (also requires TELEGRAM_BOT_TOKEN)
lein run          # start bot (requires TELEGRAM_BOT_TOKEN)
lein uberjar      # creates two JARs; deploy with *-standalone.jar
```

All Leiningen commands fail at namespace-load time if `TELEGRAM_BOT_TOKEN` is missing. This is because `api.clj` eagerly evaluates `(def bot-token (helpers/required-env "TELEGRAM_BOT_TOKEN"))` when the namespace is loaded. Set the env var before any command.

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
       ├─ /download or /dl  ──► core.clj:handle-download-command
       ├─ plain video URL   ──► core.clj:handle-download-command
       └─ other text        ──► core.clj:echo-update
```

## Module roles

| File | Role |
|------|------|
| `core.clj` | Entry point. Extracts chat/text from updates, routes commands and URLs to handlers. |
| `telegram/api.clj` | HTTP client for Telegram API. Long-polling loop, send message/video. |
| `services/video_downloader.clj` | yt-dlp process wrapper. Temp file management under `/tmp/clj-telebot/downloads`. |
| `misc/helpers.clj` | `required-env` env-var helper. |

## Code conventions

- Docstrings on every public function.
- Type hints required for Java interop (reflection warnings are treated as errors).
- Private functions use `defn-`.
- Tests access private vars via `#'namespace/var-name` reader syntax.
- Result-map convention in downloader: returns `{:success true, :file <java.io.File>}` or `{:success false, :error <string>}`.

## Testing

- `test/clj_telebot/core_test.clj` — pure function tests (message extraction, URL detection).
- `test/clj_telebot/services/video_downloader_test.clj` — URL parsing tests only (no live downloads).
- `test/clj_telebot/telegram/api_test.clj` — API URL building tests.
- `test/clj_telebot/misc/helpers_test.clj` — env var tests.

## Known quirks and issues

- **Blocking is not blocking.** `handle-download-command` sends an immediate “⏳ Downloading…” message via `send-message`, then wraps the actual download inside `(future …)` so the long-polling loop stays unblocked. The downside: no limit on concurrent downloads.
- **Debug `println` in production.** `services/video_downloader.clj` still emits `[DEBUG]` log lines during normal operation.
- **Socket timeout buffer.** `get-updates` sets Hato `:socket-timeout` to `(+ 5000 (* 1000 effective-timeout))` — gives Telegram 5 extra seconds beyond the long-poll timeout.
- **Misleading function name.** `download-twitter-video` is not Twitter-specific; it is a generic yt-dlp wrapper supporting 1000+ sites.
- **Dead code.** `api.clj` contains unused `test1` and `test2` functions (vector-iteration experiments).
- **yt-dlp file naming quirks.** The downloader must search the temp directory after yt-dlp finishes because it creates intermediate streams (e.g., `uuid.fhls-2777.mp4`, `uuid.fhls-audio-XXXX.mp4`) before merging into the final `uuid.mp4`.
