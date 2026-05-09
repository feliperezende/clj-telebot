(ns clj-telebot.telegram.api-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [clj-telebot.telegram.api :as api]))

(deftest get-api-url-test
  (testing "get-api-url builds correct Telegram API URL"
    ;; Note: This test assumes bot-token is set in the environment
    ;; We'll test the function structure by checking it returns a string
    (let [url (api/get-api-url "/getMe")]
      (is (string? url))
      (is (.startsWith url "https://api.telegram.org/bot"))
      (is (.endsWith url "/getMe"))
      (is (.contains url "/bot"))))

  (testing "get-api-url handles different endpoints"
    (let [send-message-url (api/get-api-url "/sendMessage")
          get-updates-url (api/get-api-url "/getUpdates")]
      (is (.endsWith send-message-url "/sendMessage"))
      (is (.endsWith get-updates-url "/getUpdates")))))