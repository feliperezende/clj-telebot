(ns clj-telebot.core
  (:gen-class)
  (:require
   [clj-telebot.telegram.api :as api]
   [clj-telebot.services.video-downloader :as video]))

(defn- extract-text-message
  "Extracts chat-id and text from Telegram update."
  [update]
  (let [message (:message update)
        chat-id (get-in message [:chat :id])
        text (:text message)]
    (when (and chat-id (string? text))
      {:chat-id chat-id
       :text text})))

(defn- handle-download-command
  "Handles /download command by downloading video from URL and sending it.

   Supports: Twitter/X, TikTok, Instagram, YouTube, Reddit, and 1000+ more sites
   via yt-dlp.

   Usage: /download https://x.com/user/status/123...
   Or just send the URL directly."
  [chat-id ^String text]
  (println "Processing download request from chat" chat-id)
  (let [; Send "downloading" status to user
        _ (api/send-message chat-id "⏳ Downloading video, please wait...")
        ; Extract URL and download
        result (video/extract-url-and-download text)
        ^java.io.File video-file (:file result)]

    (if (:success result)
      (try
        (println "Sending video to chat" chat-id "- file:" (.getName video-file))
        (api/send-video chat-id video-file :caption "📹 Here's your video!")
        (api/send-message chat-id "✅ Download complete!")
        (catch Exception e
          (println "Error sending video:" (.getMessage e))
          (api/send-message chat-id (str "❌ Error sending video: " (.getMessage e))))
        (finally
          ; Always clean up the temp file
          (video/cleanup-temp-file video-file)))

      ; Download failed
      (do
        (println "Download failed:" (:error result))
        (api/send-message chat-id (str "❌ " (:error result)))))))

(defn- echo-update
  "Basic echo handler - repeats what user said."
  [chat-id text]
  (api/send-message chat-id text))

(defn- contains-video-url?
  "Checks if text contains a URL from a supported video platform."
  [^String text]
  (re-find #"(?i)(https?://.*(?:x\.com|twitter\.com|tiktok\.com|instagram\.com|youtube\.com|youtu\.be|reddit\.com|facebook\.com|vimeo\.com|dailymotion\.com))" text))

(defn- process-update
  "Routes incoming updates to appropriate handlers based on command/text content."
  [update]
  (when-let [{:keys [chat-id text]} (extract-text-message update)]
    (let [^String txt text]
      (cond
        ; Check for /download command
        (or (.startsWith txt "/download")
            (.startsWith txt "/dl"))
        (handle-download-command chat-id txt)

        ; Check if message contains a video URL from supported platforms
        (contains-video-url? txt)
        (handle-download-command chat-id txt)

        ; Otherwise echo the message
        :else
        (echo-update chat-id txt)))))

(defn -main
  "Starts Telegram bot with video download support.

   Supports 1000+ video sites via yt-dlp:
   - Twitter/X (x.com, twitter.com)
   - TikTok (tiktok.com)
   - Instagram (instagram.com)
   - YouTube (youtube.com, youtu.be)
   - Reddit (reddit.com)
   - Facebook (facebook.com)
   - And many more...

   Commands:
   - /download [URL] - Download video from URL
   - /dl [URL]       - Short alias for download
   - Send URL directly - Also downloads video"
  [& args]
  (println "Starting Telegram bot with video download support...")
  (println "Supports: Twitter/X, TikTok, Instagram, YouTube, Reddit, and more")
  (println "Commands:")
  (println "  /download [URL] - Download video")
  (println "  /dl [URL]       - Short alias")
  (println "  Just send a video URL directly")
  (api/long-poll-updates process-update))
