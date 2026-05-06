(ns clj-telebot.misc.helpers
  (:require
   [clojure.string :as str]))

(defn required-env [k]
  (let [v (System/getenv k)]
    (if (str/blank? v)
      (throw (ex-info (str "Missing required env var: " k)
                      {:env-var k}))
      v)))