(ns clj-telebot.core
  (:gen-class)
  (:require
   [clj-telebot.telegram.api :as api]))

(defn- extract-text-message
  [update]
  (let [message (:message update)
        chat-id (get-in message [:chat :id])
        text (:text message)]
    (when (and chat-id (string? text))
      {:chat-id chat-id
       :text text})))

(defn- echo-update
  [update]
  (when-let [{:keys [chat-id text]} (extract-text-message update)]
    (api/send-message chat-id text)))

(defn -main
  "Starts Telegram long polling and echoes text messages."
  [& args]
  (println "Starting Telegram echo bot...")
  (api/long-poll-updates echo-update))
