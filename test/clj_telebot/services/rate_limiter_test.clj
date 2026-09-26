(ns clj-telebot.services.rate-limiter-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [clj-telebot.services.rate-limiter :as rl]))

(def ^:private counter (atom 0))
(defn- unique-chat [] (swap! counter inc))

(deftest allows-initial-request
  (is (nil? (rl/check-limit (unique-chat) 0))))

(deftest allows-burst-up-to-capacity
  (let [chat-id (unique-chat)]
    (doseq [i (range 10)]
      (is (nil? (rl/check-limit chat-id 0))
          (str "Request " (inc i) " should be allowed")))))

(deftest rate-limits-after-capacity
  (let [chat-id (unique-chat)]
    (doseq [_ (range 10)]
      (rl/check-limit chat-id 0))
    (let [msg (rl/check-limit chat-id 0)]
      (is (string? msg))
      (is (re-find #"Rate limit reached" msg)))))

(deftest refills-one-token-over-time
  (let [chat-id (unique-chat)]
    (doseq [_ (range 10)]
      (rl/check-limit chat-id 0))
    (is (nil? (rl/check-limit chat-id 12001))
        "Should refill 1 token after 12 seconds at 5/min")))

(deftest rate-limited-until-full-refill
  (let [chat-id (unique-chat)]
    (doseq [_ (range 10)]
      (rl/check-limit chat-id 0))
    ;; Half a token refilled — still rate-limited
    (is (string? (rl/check-limit chat-id 6000))
        "Half a token is not enough")
    ;; Full token refilled — allowed again
    (is (nil? (rl/check-limit chat-id 12001))
        "One full token should be available"))
  (let [chat-id (unique-chat)]
    (doseq [_ (range 10)]
      (rl/check-limit chat-id 0))
    ;; Way more than enough time — refilled to capacity
    (is (nil? (rl/check-limit chat-id 300000))
        "Should refill to full capacity after 5 minutes")))

(deftest retry-after-in-message
  (let [chat-id (unique-chat)]
    (doseq [_ (range 10)]
      (rl/check-limit chat-id 0))
    (let [msg (rl/check-limit chat-id 0)]
      (is (re-find #"12 seconds" msg)))))

(deftest rate-limit-is-per-chat
  (let [chat-a (unique-chat)
        chat-b (unique-chat)]
    ;; Exhaust chat-a
    (doseq [_ (range 10)]
      (rl/check-limit chat-a 0))
    ;; chat-b should be unaffected
    (is (nil? (rl/check-limit chat-b 0))
        "Different chat should not be rate-limited")))

(deftest resets-after-rest
  (testing "New chat-id starts fresh regardless of buckets atom state"
    (is (nil? (rl/check-limit (unique-chat) 0)))))