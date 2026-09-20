(ns clj-telebot.telegram.api-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [clj-telebot.telegram.api :as api]))

(deftest get-api-url-test
  (testing "get-api-url builds correct Telegram API URL"
    ;; Mock lazy token so this test runs without TELEGRAM_BOT_TOKEN set.
    (with-redefs [api/bot-token (fn [] "TEST")]
      (let [url (api/get-api-url "/getMe")]
        (is (string? url))
        (is (.startsWith url "https://api.telegram.org/bot"))
        (is (.endsWith url "/getMe"))
        (is (.contains url "/bot"))
        (is (.contains url "TEST")))))

  (testing "get-api-url handles different endpoints"
    (with-redefs [api/bot-token (fn [] "TEST")]
      (let [send-message-url (api/get-api-url "/sendMessage")
            get-updates-url (api/get-api-url "/getUpdates")]
        (is (.endsWith send-message-url "/sendMessage"))
        (is (.endsWith get-updates-url "/getUpdates"))))))