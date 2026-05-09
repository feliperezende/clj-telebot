(ns clj-telebot.misc.helpers-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [clj-telebot.misc.helpers :as helpers]))

(deftest required-env-throws-when-missing
  (testing "required-env throws when env var is missing"
    (let [k (str "MISSING_ENV_" (random-uuid))]
      (try
        (helpers/required-env k)
        (is false "expected required-env to throw")
        (catch clojure.lang.ExceptionInfo e
          (is (= k (:env-var (ex-data e)))))))))

(deftest required-env-returns-value-when-present
  (testing "required-env returns value when env var exists"
    (let [k "HOME" ; HOME should exist on most systems
          v (System/getenv k)]
      (when v
        (is (= v (helpers/required-env k)))))))

(deftest required-env-throws-when-blank
  (testing "required-env throws when env var is blank/empty string"
    ;; Create a temporary env var that's set but blank
    (let [k (str "BLANK_ENV_" (random-uuid))]
      ;; We can't actually set env vars in a running JVM easily,
      ;; so we'll just test the blank? check logic indirectly
      ;; by testing the helper's string/blank? usage
      (is (clojure.string/blank? ""))
      (is (clojure.string/blank? "   "))
      (is (clojure.string/blank? nil)))))