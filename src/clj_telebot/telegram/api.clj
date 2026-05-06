(ns clj-telebot.telegram.api
  (:require
   [hato.client :as http]
   [clj-telebot.misc.helpers :as helpers]))

(def bot-token (helpers/required-env "TELEGRAM_BOT_TOKEN"))
(def telegram-api "https://api.telegram.org/bot")
(def default-long-poll-timeout-seconds 30)

(defn get-api-url
  [method]
  (str telegram-api bot-token method))

(defn do-get
  ([endpoint]
   (do-get endpoint {}))
  ([endpoint request-options]
   (-> (http/get (get-api-url endpoint)
                  (merge {:as :json} request-options))
        :body)))

(defn do-post
  ([endpoint]
   (do-post endpoint {}))
  ([endpoint request-options]
   (-> (http/post (get-api-url endpoint)
                  (merge {:as :json} request-options))
       :body)))

(defn get-me
  []
  (do-get "/getMe"))

(defn send-message
  [chat-id text]
  (do-post "/sendMessage"
           {:form-params {"chat_id" chat-id
                          "text" text}}))

(defn get-updates
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
              :socket-timeout (+ 5000 (* 1000 effective-timeout))}))))

(defn get-next-offset
  [updates current-offset]
  (if (seq updates)
    (->> updates
         (map :update_id)
         (apply max)
         inc)
    current-offset))

(defn- poll-once
  [handler next-offset timeout]
  (let [updates (:result (get-updates {:offset next-offset
                                       :timeout timeout}))]
    (run! handler updates)
    (get-next-offset updates next-offset)))

(defn long-poll-updates
  ([]
   (long-poll-updates prn {}))
  ([handler]
   (long-poll-updates handler {}))
  ([handler {:keys [offset timeout error-sleep-ms]
             :or {offset 0
                  timeout default-long-poll-timeout-seconds
                  error-sleep-ms 1000}}]
   (loop [next-offset offset]
     (recur
      (try
        (println "Polling for updates with offset" next-offset "and timeout" timeout "seconds...")
        (poll-once handler next-offset timeout)
        (catch Exception e
          (binding [*out* *err*]
            (println "Long poll failed:" (.getMessage e)))
          (Thread/sleep error-sleep-ms)
          next-offset))))))
