(ns clj-telebot.services.video-downloader-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [clj-telebot.services.video-downloader :as video]))

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

;; Note: parse-url-from-text is private (defn-)
;; We test URL parsing through extract-url-and-download which will fail
;; to download but confirms the URL was extracted

(deftest url-extraction-twitter-test
  (testing "Extracts Twitter/X URLs"
    ;; These will fail to download (no network in tests) but should extract the URL
    (let [test-urls ["https://x.com/user/status/123"
                     "https://twitter.com/user/status/123"
                     "http://x.com/user/status/123"]]
      (doseq [url test-urls]
        (let [result (video/extract-url-and-download url)]
          ;; Should attempt download (will fail without network, but not "no URL found")
          (is (not (.contains (:error result) "No supported video URL found"))
              (str "Should have extracted URL from: " url)))))))

(deftest url-extraction-tiktok-test
  (testing "Extracts TikTok URLs"
    (let [result (video/extract-url-and-download "https://tiktok.com/@user/video/123")]
      (is (not (.contains (:error result) "No supported video URL found"))))))

(deftest url-extraction-instagram-test
  (testing "Extracts Instagram URLs"
    (let [result (video/extract-url-and-download "https://instagram.com/p/ABC123/")]
      (is (not (.contains (:error result) "No supported video URL found"))))))

(deftest url-extraction-youtube-test
  (testing "Extracts YouTube URLs"
    (let [test-urls ["https://youtube.com/watch?v=ABC123"
                     "https://youtu.be/ABC123"
                     "https://www.youtube.com/watch?v=ABC123"]]
      (doseq [url test-urls]
        (let [result (video/extract-url-and-download url)]
          (is (not (.contains (:error result) "No supported video URL found"))
              (str "Should have extracted URL from: " url)))))))

(deftest url-extraction-reddit-test
  (testing "Extracts Reddit URLs"
    (let [result (video/extract-url-and-download "https://reddit.com/r/videos/comments/123/abc/")]
      (is (not (.contains (:error result) "No supported video URL found"))))))

(deftest url-extraction-unsupported-site-test
  (testing "Returns nil for unsupported sites"
    (let [result (video/extract-url-and-download "https://example.com/video.mp4")]
      (is (false? (:success result)))
      (is (.contains (:error result) "No supported video URL found")))))

(deftest url-extraction-in-mixed-text-test
  (testing "Extracts URL from mixed text"
    (let [text "Check out this cool video! https://x.com/user/status/123 It's amazing!"
          result (video/extract-url-and-download text)]
      (is (not (.contains (:error result) "No supported video URL found"))))))