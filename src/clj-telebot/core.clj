(ns test.core
  (:gen-class)
  (:require
   [hato.client :as http]
   [clojure.pprint :as pp]))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))

(def BOTTOKEN "8218400675:AAHputMhHOayrWtFxzt0XfEPA6gqMBXA1S0")
(def TELEGRAM-API "https://api.telegram.org/bot")

(defn getAPIURL
  [method]
  (str TELEGRAM-API BOTTOKEN method))

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
