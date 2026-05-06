(ns clj-telebot.core-test
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
