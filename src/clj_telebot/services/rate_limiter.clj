(ns clj-telebot.services.rate-limiter
  "Per-chat token bucket rate limiter.

   Each chat has a bucket with `capacity` tokens (max burst) that refills
   at `refill-rate` tokens per minute. When the bucket is empty, the chat
   is rate-limited with a message indicating how long to wait.

   Configuration via env vars (with defaults):
     RATE_LIMIT_CAPACITY      - Max burst (default: 10)
     RATE_LIMIT_REFILL_RATE   - Tokens per minute (default: 5)"
  (:require
   [clojure.string :as str]))

(def capacity
  "Maximum token burst per chat."
  (let [v (System/getenv "RATE_LIMIT_CAPACITY")]
    (if (str/blank? v) 10 (Double/parseDouble v))))

(def refill-rate
  "Tokens refilled per minute."
  (let [v (System/getenv "RATE_LIMIT_REFILL_RATE")]
    (if (str/blank? v) 5 (Double/parseDouble v))))

;; Atom holding map of chat-id -> {:tokens N :last-refill ts}.
(defonce ^:private buckets (atom {}))

(defn- format-rate-limit-message
  "Formats a human-readable rate limit message with retry time."
  [retry-after-ms]
  (let [seconds (long (Math/ceil (/ retry-after-ms 1000.0)))]
    (if (>= seconds 60)
      (let [minutes (long (Math/ceil (/ seconds 60)))]
        (str "Rate limit reached. Try again in " minutes " minute"
             (when (> minutes 1) "s") "."))
      (str "Rate limit reached. Try again in " seconds " second"
           (when (> seconds 1) "s") "."))))

(defn check-limit
  "Checks if chat-id is within its rate limit.

   Returns nil if the request is allowed (consumes one token).
   Returns a string message if rate-limited.

   The two-argument variant accepts an explicit timestamp for testing."
  ([chat-id]
   (check-limit chat-id (System/currentTimeMillis)))
  ([chat-id now-ms]
   (let [retry-after (atom nil)]
     (swap! buckets
       (fn [buckets]
         (let [bucket (get buckets chat-id
                           {:tokens (double capacity)
                            :last-refill (double now-ms)})
               elapsed (max 0 (- (double now-ms) (:last-refill bucket)))
               refill-tokens (* elapsed (/ refill-rate 60000.0))
               new-tokens (min (double capacity) (+ (:tokens bucket) refill-tokens))]
           (if (>= new-tokens 1.0)
             (assoc buckets chat-id
                    {:tokens (- new-tokens 1.0)
                     :last-refill (double now-ms)})
             (do (reset! retry-after
                         (long (Math/ceil
                                (* (- 1.0 new-tokens)
                                   (/ 60000.0 refill-rate)))))
                 buckets)))))
     (when @retry-after
       (format-rate-limit-message @retry-after)))))