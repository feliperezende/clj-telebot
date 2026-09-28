# Change Log

All notable changes to this project will be documented in this file. This change log follows the conventions of [Keep a Changelog](https://keepachangelog.com/).

## [Unreleased]

## [0.3.1] - 2026-09-28

### Changed

- Align README, CHANGELOG, and AGENTS with current v0.3.x behavior (commands, rate limits, download pool, CI).
- Fix `project.clj` `:url` to `https://github.com/feliperezende/clj-telebot`.

## [0.3.0] - 2026-09-26

### Added

- Per-chat token-bucket rate limiter (`services/rate_limiter.clj`) with env configuration (`RATE_LIMIT_CAPACITY`, `RATE_LIMIT_REFILL_RATE`, `RATE_LIMIT_STALE_THRESHOLD`, `RATE_LIMIT_STALE_TTL_MINUTES`).
- Version-bump convention in `AGENTS.md` (every PR bumps `project.clj`).

### Fixed

- Catch exceptions in `process-update` so long-poll offset always advances.
- Evict idle rate-limit buckets when the map grows past the stale threshold.

## [0.2.0] - 2026-09-20

### Added

- CI pipeline: `lein test` on PRs; uberjar build, artifact upload, and GitHub Release on pushes to `master`.
- Bounded download pool (`ThreadPoolExecutor`, max 3 concurrent) with busy rejection when saturated.
- Process cleanup for hung yt-dlp downloads; lazy `TELEGRAM_BOT_TOKEN` so namespace load / tests do not require a real token.

### Changed

- Hermetic tests that no longer need a live Telegram token at load time.

## [0.1.0] - 2026-05-05

### Added

- Long-polling Telegram bot client (Hato + Cheshire).
- Echo handler for plain text messages.
- Video download via yt-dlp (`/download`, `/dl`, or auto-detect supported URLs).
- Bot command management API helpers (`set-my-commands`, `get-my-commands`, `delete-my-commands`).
- `/uptime` command reporting JVM process uptime.
