(ns clj-telebot.telegram.api
  (:require
   [hato.client :as http]
   [clojure.pprint :as pp]
   [clj-telebot.misc.helpers :as helpers]))

(def BOT-TOKEN (helpers/required-env "TELEGRAM_BOT_TOKEN"))
(def TELEGRAM-API "https://api.telegram.org/bot")

(defn getAPIURL
  [method]
  (str TELEGRAM-API BOT-TOKEN method))

(defn doGet
  [httpGet]
  (-> (http/get (getAPIURL httpGet) {:as :json})
      :body))

(defn getMe
  []
  (doGet "/getMe"))

(defn getResult
  []
  (let [result (getMe)]
    (pp/pprint result)))

(defn getUpdates
  []
  (doGet "/getUpdates"))
