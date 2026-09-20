(ns clj-telebot.services.video-downloader-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [clj-telebot.services.video-downloader :as video]))

;; parse-url-from-text is private (defn-) — access via #' like other tests.
;; These tests are hermetic: they never spawn yt-dlp or hit the network.

(def ^:private parse-url
  @#'video/parse-url-from-text)

(deftest extract-url-and-download-no-url-test
  (testing "Returns error when no URL in text"
    (let [result (video/extract-url-and-download "Just some text without a URL")]
      (is (false? (:success result)))
      (is (string? (:error result)))
      (is (.contains (:error result) "No supported video URL found"))))

  (testing "Returns error for empty text"
    (let [result (video/extract-url-and-download "")]
      (is (false? (:success result)))
      (is (string? (:error result)))))

  (testing "Returns error for nil"
    (let [result (video/extract-url-and-download nil)]
      (is (false? (:success result)))
      (is (string? (:error result))))))

(deftest url-extraction-supported-sites-test
  (testing "Extracts supported video URLs without network"
    (doseq [url ["https://x.com/user/status/123"
                 "https://twitter.com/user/status/123"
                 "http://x.com/user/status/123"
                 "https://tiktok.com/@user/video/123"
                 "https://instagram.com/p/ABC123/"
                 "https://youtube.com/watch?v=ABC123"
                 "https://youtu.be/ABC123"
                 "https://www.youtube.com/watch?v=ABC123"
                 "https://reddit.com/r/videos/comments/123/abc/"
                 "https://facebook.com/watch?v=123"
                 "https://vimeo.com/123456"
                 "https://dailymotion.com/video/abc123"]]
      (is (= url (parse-url url))
          (str "Should have extracted URL from: " url)))))

(deftest url-extraction-unsupported-site-test
  (testing "Returns nil for unsupported sites"
    (is (nil? (parse-url "https://example.com/video.mp4")))
    (is (nil? (parse-url "Just some text without a URL")))
    (is (nil? (parse-url "")))
    (is (nil? (parse-url nil))))
  (testing "extract-url-and-download returns friendly error for unsupported sites"
    (let [result (video/extract-url-and-download "https://example.com/video.mp4")]
      (is (false? (:success result)))
      (is (.contains (:error result) "No supported video URL found")))))

(deftest url-extraction-in-mixed-text-test
  (testing "Extracts URL from mixed text"
    (let [text "Check out this cool video! https://x.com/user/status/123 It's amazing!"
          url (parse-url text)]
      (is (string? url))
      (is (.startsWith url "https://x.com/user/status/123")))))

(deftest extract-url-and-download-delegates-test
  (testing "Delegates extracted URL to download fn (mocked, no network)"
    (let [seen (atom nil)
          fake-file (java.io.File. "/tmp/fake.mp4")]
      (with-redefs [video/download-twitter-video
                    (fn [url] (reset! seen url) {:success true :file fake-file})]
        (let [result (video/extract-url-and-download "see https://x.com/user/status/123 ok")]
          (is (= "https://x.com/user/status/123" @seen))
          (is (true? (:success result)))
          (is (= fake-file (:file result))))))))
