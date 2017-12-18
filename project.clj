(defproject pomodoro "0.1.0-SNAPSHOT"
  :description "A Slack bot that helps you focus"
  :url ""
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [clj-slack-client "0.1.7-SNAPSHOT"]
                 [clj-time "0.14.2"]
                 [cheshire "5.8.0"]
                 [http-kit "2.2.0"]]
  :main ^:skip-aot pomodoro.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
