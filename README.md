# clj-telebot

Clojure Telegram bot that long-polls the Bot API (Hato + Cheshire). It echoes text, reports uptime, and downloads videos via yt-dlp with per-chat rate limiting.

## Requirements

- Java 8+ (CI uses Temurin 21)
- [Leiningen](https://leiningen.org/)
- A Telegram bot token from [@BotFather](https://t.me/BotFather)
- [`yt-dlp`](https://github.com/yt-dlp/yt-dlp) on `PATH` (video downloads)
- [`ffmpeg`](https://ffmpeg.org/) on `PATH` (yt-dlp merges separate audio/video streams)

## Configuration

Required:

```bash
export TELEGRAM_BOT_TOKEN="your_bot_token"
```

If `TELEGRAM_BOT_TOKEN` is missing or blank, the first Telegram API call throws:

```text
Missing required env var: TELEGRAM_BOT_TOKEN
```

Optional rate-limit env vars (per-chat token bucket):

| Variable | Default | Meaning |
|----------|---------|---------|
| `RATE_LIMIT_CAPACITY` | `10` | Max burst tokens per chat |
| `RATE_LIMIT_REFILL_RATE` | `5` | Tokens refilled per minute |
| `RATE_LIMIT_STALE_THRESHOLD` | `1000` | Evict idle buckets when map exceeds this size |
| `RATE_LIMIT_STALE_TTL_MINUTES` | `60` | Idle bucket TTL before eviction |

## Run

```bash
lein run
```

Or build and run the standalone uberjar:

```bash
lein uberjar
java -jar target/uberjar/clj-telebot-*-standalone.jar
```

## Commands

| Input | Behavior |
|-------|----------|
| `/download <url>` or `/dl <url>` | Queue a yt-dlp download (max 3 concurrent) |
| Plain message with a supported video URL | Same as download |
| `/uptime` | Report JVM process uptime |
| Other text | Echo the message back |

Supported hosts for auto-detect / download: `x.com`, `twitter.com`, `tiktok.com`, `instagram.com`, `youtube.com` / `youtu.be`, `reddit.com`, `facebook.com`, `vimeo.com`, `dailymotion.com` (yt-dlp itself supports many more once a URL is accepted).

Downloads land under `/tmp/clj-telebot/downloads`, then are sent to the chat and cleaned up. When the download pool is full, the bot replies that the server is busy.

## REPL usage

```clojure
(require '[clj-telebot.telegram.api :as api])

(api/get-me)
(api/get-updates)
(api/send-message 123456789 "hello")

;; long polling with default timeout (30s)
(api/long-poll-updates prn)

;; long polling with custom options
(api/long-poll-updates prn {:offset 0 :timeout 30 :error-sleep-ms 1000})
```

HTTP responses use Hato `{:as :json}`, so `:body` is already a Clojure map.

## Project structure

- `src/clj_telebot/core.clj` — entrypoint, command routing, download pool
- `src/clj_telebot/telegram/api.clj` — Telegram HTTP client and long-poll loop
- `src/clj_telebot/services/video_downloader.clj` — yt-dlp wrapper and temp files
- `src/clj_telebot/services/rate_limiter.clj` — per-chat token bucket
- `src/clj_telebot/misc/helpers.clj` — env-var helper

## CI

GitHub Actions (`.github/workflows/build.yml`):

- **pull_request** to `master`: run `lein test`
- **push** to `master`: test, build uberjar, upload artifact, publish a GitHub Release (`build-<run_number>`)

## License

Copyright © 2026

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
