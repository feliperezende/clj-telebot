(ns clj-telebot.core
  (:gen-class)
  (:require
   [clj-telebot.telegram.api :as api]))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println (api/getMe)))
