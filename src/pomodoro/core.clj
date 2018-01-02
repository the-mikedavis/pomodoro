(ns pomodoro.core
  (:gen-class)
  (:require
    [clj-slack-client
     [core :as slack]
     [team-state :as team]]
    [pomodoro
     [reply :as reply]
     [state :as state]
     [interact :as interact]]))


(defn handle-slack-event
  "Reply to a slack message. Do nothing if an event isn't a message.
  Essentially just pass off the event map to the reply/handle-message
  function."
  [{user-talking :user, :as event}]
  (when (and (= (:type event) "message")
             (not (team/bot? user-talking)))
    (reply/handle-message event)))


(defn try-handle-slack-event
  [event]
  (try
    (handle-slack-event event)
    (catch Exception ex
      (interact/printex (str "Exception trying to handle slack event\n"
                             (str event) ".") ex))))


(defn wait-for-console-quit []
  "Continuously read in lines from stdin until it's just 'q'"
  (loop []
    (let [input (read-line)]
      (when-not (= input "q")
        (recur)))))


(defn shutdown-app []
  (slack/disconnect)
  (println "...pomodoro dying")
  (shutdown-agents))


(defn start []
  (try
    (slack/connect state/api-token try-handle-slack-event)
    (println "pomodoro running...")
    (catch Exception ex
      (println (str "Couldn't start pomodoro due to a connection problem.\n"
                    "Check your internet connection and try again."))
      (shutdown-app)
      (System/exit 1))))


(defn -main
  [& args]
  (try
    (start)
    (wait-for-console-quit)
    (finally (shutdown-app))))
