(ns clj-telebot.core
  (:gen-class)
  (:require
   [clj-telebot.telegram.api :as api]
   [clj-telebot.services.video-downloader :as video])
  (:import
   [java.lang.management ManagementFactory]))

(defn- extract-text-message
  "Extracts chat-id and text from Telegram update."
  [update]
  (let [message (:message update)
        chat-id (get-in message [:chat :id])
        text (:text message)]
    (when (and chat-id (string? text))
      {:chat-id chat-id
       :text text})))

(defn- send-download-result
  "Sends the download result to the user. Called from future."
  [chat-id result]
  (let [^java.io.File video-file (:file result)]
    (try
      (if (:success result)
        (try
          (println "Sending video to chat" chat-id "- file:" (.getName video-file))
          (api/send-video chat-id video-file :caption "📹 Here's your video!")
          (api/send-message chat-id "✅ Download complete!")
          (catch Exception e
            (println "Error sending video:" (.getMessage e))
            (api/send-message chat-id (str "❌ Error sending video: " (.getMessage e))))
          (finally
            (video/cleanup-temp-file video-file)))
        ;; Download failed
        (do
          (println "Download failed for chat" chat-id ":" (:error result))
          (api/send-message chat-id (str "❌ " (:error result)))))
      (catch Exception e
        (println "Unexpected error in send-download-result for chat" chat-id ":" (.getMessage e))))))

(defn- handle-download-command
  "Handles /download command by downloading video from URL asynchronously.

   Supports: Twitter/X, TikTok, Instagram, YouTube, Reddit, and 1000+ more sites
   via yt-dlp.

   Downloads run in background so bot can continue processing other messages.

   Usage: /download https://x.com/user/status/123...
   Or just send the URL directly."
  [chat-id ^String text]
  (println "Queuing download request from chat" chat-id)
  ;; Send immediate confirmation
  (api/send-message chat-id "⏳ Downloading video, please wait...")
  ;; Start download in background thread
  (future
    (try
      (let [result (video/extract-url-and-download text)]
        (send-download-result chat-id result))
      (catch Exception e
        (println "Error in download future for chat" chat-id ":" (.getMessage e))
        (try
          (api/send-message chat-id "❌ An unexpected error occurred during download.")
          (catch Exception _ nil))))))

(defn- echo-update
  "Basic echo handler - repeats what user said."
  [chat-id text]
  (api/send-message chat-id text))

(defn- format-uptime
  "Formats milliseconds as human-readable uptime string."
  [millis]
  (let [total-seconds (long (/ millis 1000))
        days (quot total-seconds 86400)
        hours (quot (mod total-seconds 86400) 3600)
        minutes (quot (mod total-seconds 3600) 60)
        seconds (mod total-seconds 60)]
    (str "🤖 Bot uptime: "
         (when (pos? days) (str days "d "))
         (format "%02dh %02dm %02ds" hours minutes seconds))))

(defn- handle-uptime-command
  "Handles /uptime command by sending bot process uptime."
  [chat-id]
  (let [uptime-ms (.getUptime (ManagementFactory/getRuntimeMXBean))]
    (api/send-message chat-id (format-uptime uptime-ms))))

(defn- contains-video-url?
  "Checks if text contains a URL from a supported video platform."
  [^String text]
  (re-find #"(?i)(https?://.*(?:x\.com|twitter\.com|tiktok\.com|instagram\.com|youtube\.com|youtu\.be|reddit\.com|facebook\.com|vimeo\.com|dailymotion\.com))" text))

(defn- process-update
  "Routes incoming updates to appropriate handlers based on command/text content.

   Download commands run asynchronously so the bot can handle multiple
   concurrent downloads without blocking."
  [update]
  (when-let [{:keys [chat-id text]} (extract-text-message update)]
    (let [^String txt text]
      (cond
        ;; Check for /download command
        (or (.startsWith txt "/download")
            (.startsWith txt "/dl"))
        (handle-download-command chat-id txt)

        ;; Check if message contains a video URL from supported platforms
        (contains-video-url? txt)
        (handle-download-command chat-id txt)

        ;; Check for /uptime command
        (.startsWith txt "/uptime")
        (handle-uptime-command chat-id)

        ;; Otherwise echo the message
        :else
        (echo-update chat-id txt)))))

(defn -main
  "Starts Telegram bot with async video download support.

   Supports 1000+ video sites via yt-dlp:
   - Twitter/X (x.com, twitter.com)
   - TikTok (tiktok.com)
   - Instagram (instagram.com)
   - YouTube (youtube.com, youtu.be)
   - Reddit (reddit.com)
   - Facebook (facebook.com)
   - And many more...

   Downloads run asynchronously - bot can handle multiple concurrent downloads.

   Commands:
   - /download [URL] - Download video from URL
   - /dl [URL]       - Short alias for download
   - /uptime         - Show bot uptime
   - Send URL directly - Also downloads video"
  [& args]
  (println "Starting Telegram bot with async video download support...")
  (println "Supports: Twitter/X, TikTok, Instagram, YouTube, Reddit, and more")
  (println "Downloads run asynchronously - multiple users can download at once!")
  (println "Commands:")
  (println "  /download [URL] - Download video")
  (println "  /dl [URL]       - Short alias")
  (println "  /uptime         - Show bot uptime")
  (println "  Just send a video URL directly")
  (api/long-poll-updates process-update))
