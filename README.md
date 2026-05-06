# clj-telebot

Small Clojure Telegram bot client using the Telegram HTTP API.

## Requirements

- Java 8+
- Leiningen
- A Telegram bot token from BotFather

## Configuration

Set the required environment variable before running:

```bash
export TELEGRAM_BOT_TOKEN="your_bot_token"
```

If `TELEGRAM_BOT_TOKEN` is missing or blank, the app throws:

```text
Missing required env var: TELEGRAM_BOT_TOKEN
```

## Run

Start the app:

```bash
lein run
```

The current `-main` starts a long-polling loop and echoes text messages back to each chat.

## REPL Usage

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

HTTP responses are requested with Hato using `{:as :json}`, so `:body` is already a Clojure map.

## Project Structure

- `src/clj_telebot/core.clj` entrypoint
- `src/clj_telebot/telegram/api.clj` Telegram API helpers
- `src/clj_telebot/misc/helpers.clj` environment variable utilities

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
