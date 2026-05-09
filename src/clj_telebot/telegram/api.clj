(ns clj-telebot.telegram.api
  "Telegram Bot API client with long-polling support.

   Provides functions to interact with the Telegram Bot API including
   receiving updates via long polling and sending messages back to chats."
  (:require
   [hato.client :as http]
   [clj-telebot.misc.helpers :as helpers]))

;; Configuration constants loaded from environment

(def bot-token
  "Bot authentication token from BotFather.
   Loaded from TELEGRAM_BOT_TOKEN environment variable at startup."
  (helpers/required-env "TELEGRAM_BOT_TOKEN"))

(def telegram-api
  "Base URL for Telegram Bot API endpoints."
  "https://api.telegram.org/bot")

(def default-long-poll-timeout-seconds
  "Default timeout for long polling requests (30 seconds).
   Telegram will hold the connection open up to this duration
   and return immediately when new messages arrive."
  30)

;; URL building

(defn get-api-url
  "Builds full API URL by appending method path to base URL with bot token.

   Example: (get-api-url \"/getMe\")
   Returns: \"https://api.telegram.org/bot<token>/getMe\""
  [method]
  (str telegram-api bot-token method))

;; HTTP request helpers

(defn do-get
  "Performs HTTP GET request to Telegram API.

   Args:
     endpoint        - API method path like \"/getMe\"
     request-options - Optional map merged into hato request config

   Returns parsed JSON response body as Clojure data structure."
  ([endpoint]
   (do-get endpoint {}))
  ([endpoint request-options]
   (-> (http/get (get-api-url endpoint)
                 (merge {:as :json} request-options))
       :body)))

(defn do-post
  "Performs HTTP POST request to Telegram API.

   Args:
     endpoint        - API method path like \"/sendMessage\"
     request-options - Optional map merged into hato request config
                       Typically includes :form-params for POST body

   Returns parsed JSON response body as Clojure data structure."
  ([endpoint]
   (do-post endpoint {}))
  ([endpoint request-options]
   (-> (http/post (get-api-url endpoint)
                  (merge {:as :json} request-options))
       :body)))

;; Telegram API methods

(defn get-me
  "Tests bot authentication token and returns basic bot info.

   Returns map with :ok boolean and :result containing
   bot details like :id, :first_name, :username."
  []
  (do-get "/getMe"))

(defn send-message
  "Sends text message to a specific chat.

   Args:
     chat-id - Target chat ID (number or string for channels)
     text    - Message text content

   Returns API response with sent message details including
   :message_id assigned by Telegram."
  [chat-id text]
  (do-post "/sendMessage"
           {:form-params {"chat_id" chat-id
                          "text" text}}))

(defn send-video
  "Sends video file to a specific chat.

   Args:
     chat-id  - Target chat ID (number or string for channels)
     video    - File object (java.io.File) to send
     options  - Optional map with additional parameters:
                :caption - Video caption (optional)
                :supports_streaming - Boolean, whether video is suitable for streaming

   Returns API response with sent message details."
  [chat-id ^java.io.File video & {:keys [caption supports-streaming]}]
  (do-post "/sendVideo"
           {:multipart [{:name "chat_id" :content (str chat-id)}
                         {:name "video" :content video :filename (.getName video)}]
            :form-params (cond-> {}
                         caption (assoc "caption" caption)
                         supports-streaming (assoc "supports_streaming" supports-streaming))}))

(defn get-updates
  "Receives incoming updates from Telegram using long polling.

   Args (as map):
     :offset  - Update ID to start from (skips earlier updates)
     :limit   - Maximum updates to fetch (1-100)
     :timeout - Seconds to wait for new updates (0 = immediate return)

   Returns map with :ok boolean and :result containing vector
   of update objects. Empty result means no new updates within timeout."
  ([]
   (get-updates {}))
  ([{:keys [offset limit timeout]
     :or {timeout 0}}]
   (let [effective-timeout (if (number? timeout)
                             (max timeout 0)
                             0)
         query-params (cond-> {}
                        (some? offset) (assoc :offset offset)
                        (some? limit) (assoc :limit limit)
                        (some? timeout) (assoc :timeout effective-timeout))]
     (do-get "/getUpdates"
             {:query-params query-params
              ;; Add 5s buffer to socket timeout so Telegram has time to respond
              :socket-timeout (+ 5000 (* 1000 effective-timeout))}))))

;; Update processing utilities

(defn get-next-offset
  "Calculates next offset value after processing a batch of updates.

   Args:
     updates       - Vector of update objects from Telegram
     current-offset - The offset that was used to fetch this batch

   Returns:
     If updates exist: (max update_id) + 1 to skip processed updates
     If no updates: current-offset to retry from same position"
  [updates current-offset]
  (if (seq updates)
    (->> updates
         (map :update_id)
         (apply max)
         inc)
    current-offset))

;; Long polling implementation

(defn- poll-once
  "Single polling iteration: fetches updates, processes them, returns next offset.

   Args:
     handler     - Function called for each update (typically processes message)
     next-offset - Offset value to request from
     timeout     - Seconds for Telegram to hold the connection

   Side effects:
     - Calls handler for each received update
     - Blocks until response or timeout

   Returns:
     Next offset value to use for subsequent poll"
  [handler next-offset timeout]
  (let [updates (:result (get-updates {:offset next-offset
                                       :timeout timeout}))]
    (run! handler updates)
    (get-next-offset updates next-offset)))

(defn- safe-poll
  "Protected single poll with error handling and retry delay.

   Args:
     handler        - Update handler function
     offset         - Current offset value
     timeout        - Polling timeout in seconds
     error-sleep-ms - Milliseconds to sleep after error before retry

   Side effects:
     - Calls handler for updates on success
     - Prints error to stderr on failure
     - Sleeps specified duration after error

   Returns:
     Next offset on success, same offset on failure (for retry)"
  [handler offset timeout error-sleep-ms]
  (try
    (poll-once handler offset timeout)
    (catch Exception e
      (binding [*out* *err*]
        (println "Long poll failed:" (.getMessage e)))
      (Thread/sleep error-sleep-ms)
      offset)))

(defn long-poll-updates
  "Infinite long-polling loop for receiving Telegram updates.

   Supports 3 arities for convenience:
   - ([]): Uses prn as handler with default options
   - ([handler]): Custom handler with default options
   - ([handler options]): Full control

   Options map keys:
     :offset         - Starting offset (default: 0)
     :timeout        - Polling timeout seconds (default: 30)
     :error-sleep-ms - Sleep after error, milliseconds (default: 1000)

   The handler function receives each raw update map from Telegram.
   Typical handler extracts :message and replies via send-message.

   This function never returns under normal operation."
  ([]
   (long-poll-updates prn {}))
  ([handler]
   (long-poll-updates handler {}))
  ([handler {:keys [offset timeout error-sleep-ms]
             :or {offset 0
                  timeout default-long-poll-timeout-seconds
                  error-sleep-ms 1000}}]
   (println "Long polling with offset" offset "and timeout" timeout "seconds...")
   (loop [offset offset]
     (recur (safe-poll handler offset timeout error-sleep-ms)))))

(defn test1
  [vec]
  (loop [v vec]
    (when (seq v)
      (println "Processing:" (first v))
      (Thread/sleep 1000) ;; Simulate work
      (recur (rest v)))))

(defn test2
  [vec]
  (loop [v vec]
    (when (seq v)
      (let [[head & tail] v]
        (println "Processing:" head)
        (Thread/sleep 1000) ;; Simulate work
        (recur tail)))))