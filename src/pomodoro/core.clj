(ns pomodoro.core
  (:gen-class)
  (:require
    [clj-slack-client
     [core :as slack]
     [rtm-transmit :as tx]
     [team-state :as team]
     [web :as web]]
    [pomodoro
     [reply :as reply]
     [state :as state]
     [interact :as interact]]
    [clj-time.core :as time]
    [clojure.string :as string]
    [org.httpkit.client :as http]))

(defn dispatch-handle-slack-event [event] ((juxt :type :subtype) event))

(defmulti handle-slack-event #'dispatch-handle-slack-event)

; all reply work gets done here
(defmethod handle-slack-event ["message" nil]
  [{user-id :user, :as msg}]
  (when (not (team/bot? user-id))
    (reply/handle-message msg)))

(defmethod handle-slack-event ["channel_joined" nil]
  [event]
  nil)

(defmethod handle-slack-event :default
  [event]
  nil)

(defn try-handle-slack-event
  [event]
  (try
    (handle-slack-event event)
    (catch Exception ex
      (interact/printex (str "Exception trying to handle slack event\n"
                    (str event) ".") ex))))

(def heartbeating (atom false))

(def heartbeat-loop (atom nil))

(defn handle-command
  "Not sure what this is supposed to do"
  [arg]
  arg)

(defn heartbeat []
  (handle-command {:command-type :find-nags
                   :date         (time/today)
                   :ts           (slack/time->ts (time/now))}))


(defn start-heartbeat []
  (swap! heartbeating (constantly true))
  (swap! heartbeat-loop (constantly
                          (future
                            (loop []
                              (heartbeat)
                              (Thread/sleep 5000)
                              (when @heartbeating (recur)))))))

(defn stop-heartbeat []
  "kill the heartbeat loop and block until the loop exits"
  (swap! heartbeating (constantly false))
  (future-cancel @heartbeat-loop))


(defn wait-for-console-quit []
  (loop []
    (let [input (read-line)]
      (when-not (= input "q")
        (recur)))))


(defn shutdown-app []
  (stop-heartbeat)
  (slack/disconnect)
  (println "...pomodoro dying"))


(defn stop []
  (shutdown-app))

(defn start
  ([]
   (start state/api-token))
  ([api-token]
   (try
     (slack/connect state/api-token try-handle-slack-event)
     (start-heartbeat)
     (println "pomodoro running...")
     (catch Exception ex
       (println ex)
       (println "couldn't start pomodoro")
       (stop)))))

(defn restart []
  (stop)
  (start))


(defn -main
  [& args]
  (try
    (start)
    (wait-for-console-quit)
    (finally
      (stop)
      (shutdown-agents))))
