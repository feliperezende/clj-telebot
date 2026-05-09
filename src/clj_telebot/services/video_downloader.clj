(ns clj-telebot.services.video-downloader
  "Service for downloading videos from Twitter/X using yt-dlp.

   Spawns external yt-dlp process to download videos and provides
   temporary file management."
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str])
  (:import
   [java.io File]
   [java.util UUID]
   [java.util.concurrent TimeUnit]))

(def ^File temp-download-dir
  "Directory for temporary video downloads."
  (io/file "/tmp" "clj-telebot" "downloads"))

(defn- ensure-temp-dir
  "Creates temporary download directory if it doesn't exist."
  []
  (when-not (.exists temp-download-dir)
    (.mkdirs temp-download-dir)))

(defn- generate-temp-file
  "Generates a unique temporary file path for video download.

   Returns: java.io.File instance with .mp4 extension"
  []
  (let [filename (str (UUID/randomUUID) ".mp4")]
    (io/file temp-download-dir filename)))

(defn- parse-url-from-text
  "Extracts Twitter/X URL from text message.

   Supports:
   - https://twitter.com/...
   - https://x.com/...
   - twitter.com/...
   - x.com/...

   Returns: URL string or nil if not found"
  [text]
  (when text
    (let [url-pattern #"(?:https?://)?(?:www\.)?(?:twitter\.com|x\.com)/[^\s]+"]
      (re-find url-pattern text))))

(defn download-twitter-video
  "Downloads video from Twitter/X URL using yt-dlp.

   Args:
     url - Twitter/X video URL

   Returns: {:success true :file java.io.File} on success
            {:success false :error string} on failure

   The caller is responsible for deleting the temp file when done."
  [url]
  (try
    (ensure-temp-dir)
    (let [temp-file (generate-temp-file)
          output-path (.getAbsolutePath ^File temp-file)
          ;; yt-dlp command: download best video+audio merged as mp4
          cmd ["yt-dlp"
               "-f" "best[ext=mp4]/best"  ; Best quality mp4, fallback to best
               "--merge-output-format" "mp4"
               "--output" output-path
               "--no-playlist"              ; Don't download playlists
               "--max-filesize" "100M"      ; Limit to 100MB for Telegram
               "--no-warnings"
               url]
          ^ProcessBuilder process (ProcessBuilder. ^java.util.List cmd)
          _ (.redirectErrorStream process true)
          _ (.directory process temp-download-dir)
          started (.start process)
          finished? (.waitFor started 120 TimeUnit/SECONDS)
          exit-code (when finished? (.exitValue started))]

      (if (and finished? (= 0 exit-code))
        (if (.exists ^File temp-file)
          {:success true :file temp-file}
          {:success false :error "Download completed but file not found"})
        (let [error-output (if finished?
                             (slurp (.getInputStream started))
                             "Download timed out after 120 seconds")]
          {:success false :error (str "yt-dlp failed" (when exit-code (str " with code " exit-code)) ": " error-output)})))

    (catch Exception e
      {:success false :error (str "Download error: " (.getMessage e))})))

(defn cleanup-temp-file
  "Deletes temporary video file.

   Args:
     file - java.io.File to delete

   Returns: true if deleted or didn't exist, false otherwise"
  [^File file]
  (try
    (when (and file (.exists file))
      (.delete file))
    true
    (catch Exception _
      false)))

(defn extract-url-and-download
  "Convenience function: extracts URL from text and downloads video.

   Args:
     text - Message text potentially containing Twitter/X URL

   Returns: {:success true :file java.io.File} on success
            {:success false :error string} on failure (includes no URL found)"
  [text]
  (if-let [url (parse-url-from-text text)]
    (download-twitter-video url)
    {:success false :error "No Twitter/X URL found in message. Please send a link like https://x.com/user/status/123..."}))
