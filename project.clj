(defproject clj-telebot "0.3.0"
  :description "Small Clojure Telegram bot client using Hato"
  :url "https://github.com/felipe/clj-telebot"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [hato "0.9.0"]
                 [cheshire "5.12.0"]]
  :main ^:skip-aot clj-telebot.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
