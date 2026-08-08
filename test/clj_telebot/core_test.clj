(ns clj-telebot.core-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [clj-telebot.core :as core]))

(deftest format-uptime-test
  (testing "Formats zero uptime"
    (is (= "🤖 Bot uptime: 00h 00m 00s" (#'core/format-uptime 0))))

  (testing "Formats seconds only"
    (is (= "🤖 Bot uptime: 00h 00m 45s" (#'core/format-uptime 45000))))

  (testing "Formats minutes and seconds"
    (is (= "🤖 Bot uptime: 00h 05m 30s" (#'core/format-uptime 330000))))

  (testing "Formats hours, minutes, seconds"
    (is (= "🤖 Bot uptime: 02h 15m 10s" (#'core/format-uptime 8110000))))

  (testing "Formats days"
    (is (= "🤖 Bot uptime: 3d 10h 30m 00s" (#'core/format-uptime 297000000)))))

(deftest extract-text-message-test
  (testing "Extracts chat-id and text from valid message"
    (let [update {:message {:chat {:id 123456}
                           :text "Hello world"}
                 :update_id 1}
          result (#'core/extract-text-message update)]
      (is (= 123456 (:chat-id result)))
      (is (= "Hello world" (:text result)))))

  (testing "Returns nil when no message"
    (let [update {:update_id 1}
          result (#'core/extract-text-message update)]
      (is (nil? result))))

  (testing "Returns nil when no chat-id"
    (let [update {:message {:text "Hello"}
                 :update_id 1}
          result (#'core/extract-text-message update)]
      (is (nil? result))))

  (testing "Returns nil when text is not a string"
    (let [update {:message {:chat {:id 123}
                           :text 123}
                 :update_id 1}
          result (#'core/extract-text-message update)]
      (is (nil? result))))

  (testing "Returns nil when text is nil"
    (let [update {:message {:chat {:id 123}
                           :text nil}
                 :update_id 1}
          result (#'core/extract-text-message update)]
      (is (nil? result)))))

(deftest contains-video-url-test
  (testing "Detects Twitter/X URLs"
    (is (#'core/contains-video-url? "https://x.com/user/status/123"))
    (is (#'core/contains-video-url? "https://twitter.com/user/status/123"))
    (is (#'core/contains-video-url? "Check out https://x.com/user/status/123")))

  (testing "Detects TikTok URLs"
    (is (#'core/contains-video-url? "https://tiktok.com/@user/video/123")))

  (testing "Detects Instagram URLs"
    (is (#'core/contains-video-url? "https://instagram.com/p/ABC123")))

  (testing "Detects YouTube URLs"
    (is (#'core/contains-video-url? "https://youtube.com/watch?v=ABC123"))
    (is (#'core/contains-video-url? "https://youtu.be/ABC123"))
    (is (#'core/contains-video-url? "https://www.youtube.com/watch?v=ABC123")))

  (testing "Detects Reddit URLs"
    (is (#'core/contains-video-url? "https://reddit.com/r/videos/comments/123/abc")))

  (testing "Detects Facebook URLs"
    (is (#'core/contains-video-url? "https://facebook.com/watch?v=123")))

  (testing "Detects Vimeo URLs"
    (is (#'core/contains-video-url? "https://vimeo.com/123456")))

  (testing "Detects Dailymotion URLs"
    (is (#'core/contains-video-url? "https://dailymotion.com/video/abc123")))

  (testing "Returns nil for unsupported sites"
    (is (nil? (#'core/contains-video-url? "https://example.com/video.mp4")))
    (is (nil? (#'core/contains-video-url? "Just regular text")))
    (is (nil? (#'core/contains-video-url? "")))
    (is (nil? (#'core/contains-video-url? "http://google.com"))))

  (testing "Case insensitive"
    (is (#'core/contains-video-url? "https://X.COM/user/status/123"))
    (is (#'core/contains-video-url? "https://YOUTUBE.COM/watch?v=ABC"))
    (is (#'core/contains-video-url? "https://TikTok.com/@user/video/123"))))