(ns clj-telebot.services.video-downloader
  "Service for downloading videos from various platforms using yt-dlp.

   Supports 1000+ sites including Twitter/X, TikTok, Instagram, YouTube,
   Reddit, Facebook, Vimeo, and more.

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
  "Extracts video URL from text message.

   Supports any site that yt-dlp supports:
   - Twitter/X (x.com, twitter.com)
   - TikTok (tiktok.com)
   - Instagram (instagram.com)
   - YouTube (youtube.com, youtu.be)
   - Reddit (reddit.com)
   - And 1000+ other sites

   Returns: URL string or nil if not found"
  [text]
  (when text
    ;; Match common video URL patterns
    (let [url-pattern #"https?://[^\s]+"]
      (when-let [url (re-find url-pattern text)]
        ;; Basic validation - check if it looks like a video platform
        (when (re-find #"(?i)(x\.com|twitter\.com|tiktok\.com|instagram\.com|youtube\.com|youtu\.be|reddit\.com|facebook\.com|vimeo\.com|dailymotion\.com)" url)
          url)))))

(defn- find-downloaded-file
  "Finds the downloaded file in temp directory.
   yt-dlp creates intermediate files with format IDs, then merges to final file."
  [^File expected-file]
  (let [parent (.getParentFile expected-file)
        uuid-name (.getName expected-file)  ; e.g., "uuid.mp4"
        uuid-without-ext (subs uuid-name 0 (- (.length uuid-name) 4)) ; "uuid"
        ;; List all files in temp directory
        all-files (when (.exists parent) (.listFiles parent))]
    ;; Look for final merged file first (uuid.mp4)
    ;; yt-dlp may also create uuid.fhls-XXXX.mp4 and uuid.fhls-audio-XXXX.mp4
    ;; Then merges them into uuid.mp4
    (or
     ;; First try the expected final file
     (when (.exists expected-file) expected-file)
     ;; Try without extension prefix (for template output)
     (let [candidates (filter #(and (.isFile ^File %)
                                    (.startsWith (.getName ^File %) uuid-without-ext))
                              all-files)]
       ;; Prefer .mp4 files, especially those without format IDs (merged file)
       (or (first (filter #(and (.endsWith (.getName ^File %) ".mp4")
                                (not (.contains (.getName ^File %) ".fhls-")))
                          candidates))
           ;; Any .mp4 file
           (first (filter #(.endsWith (.getName ^File %) ".mp4") candidates))
           ;; Any video file
           (first candidates))))))

(defn download-twitter-video
  "Downloads video from Twitter/X URL using yt-dlp.

   NOTE: Requires ffmpeg to be installed on the system for merging video+audio streams.

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
          ;; Remove .mp4 from path since %(ext)s will add the extension
          base-path (subs output-path 0 (- (.length output-path) 4))
          ;; Use output template: uuid.%(ext)s -> uuid.mp4
          output-template (str base-path ".%(ext)s")
          ;; Try to download pre-merged format first (avoids ffmpeg requirement)
          ;; If not available, yt-dlp will download separate streams and merge with ffmpeg
          cmd ["yt-dlp"
               "-f" "best[protocol*=m3u8]/best"  ; Try HLS streams first (pre-merged)
               "--merge-output-format" "mp4"
               "--remux-video" "mp4"      ; Force mp4 container
               "--output" output-template
               "--no-playlist"              ; Don't download playlists
               "--max-filesize" "100M"      ; Limit to 100MB for Telegram
               "--no-warnings"
               url]
          ^ProcessBuilder process (ProcessBuilder. ^java.util.List cmd)
          _ (.redirectErrorStream process true)
          _ (.directory process temp-download-dir)
          _ (println "[DEBUG] Starting yt-dlp for URL:" url)
          _ (println "[DEBUG] Output template:" output-template)
          started (.start process)
          finished? (.waitFor started 120 TimeUnit/SECONDS)
          exit-code (when finished? (.exitValue started))
          output (when finished? (slurp (.getInputStream started)))]

      (cond
        ;; Success case
        (and finished? (= 0 exit-code))
        (let [actual-file (find-downloaded-file temp-file)]
          (println "[DEBUG] Looking for downloaded file with UUID prefix:" base-path)
          (println "[DEBUG] Found file:" (when actual-file (.getAbsolutePath ^File actual-file)))
          (if actual-file
            {:success true :file actual-file}
            {:success false
             :error (str "Download completed but file not found. This usually means ffmpeg is not installed. "
                        "Please install ffmpeg. Files in temp dir: "
                        (str/join ", " (map #(.getName ^File %)
                                            (.listFiles temp-download-dir))))}))

        ;; ffmpeg not found error
        (and finished? output (.contains output "ffmpeg"))
        {:success false
         :error (str "ffmpeg is required but not installed. "
                    "Please install ffmpeg to merge video and audio streams. "
                    "Error: " (subs output 0 (min 200 (.length output))))}

        ;; Other errors
        finished?
        {:success false
         :error (str "yt-dlp failed with code " exit-code ": "
                    (subs output 0 (min 500 (.length output))))}

        ;; Timeout
        :else
        {:success false :error "Download timed out after 120 seconds"}))

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

   Supports 1000+ sites via yt-dlp.

   Args:
     text - Message text potentially containing a video URL

   Returns: {:success true :file java.io.File} on success
            {:success false :error string} on failure (includes no URL found)"
  [text]
  (if-let [url (parse-url-from-text text)]
    (download-twitter-video url)
    {:success false :error "No supported video URL found. Please send a link from Twitter/X, TikTok, Instagram, YouTube, Reddit, or other supported sites."}))
