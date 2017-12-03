(ns pomodoro.core
  (:gen-class)
  (:require
    [clj-slack-client
     [core :as slack]
     [team-state :as state]
     [web :as web]]
    [pomodoro.file-ops :as store]
    [clj-time.core :as time]
    [org.httpkit.client :as http]))

(def api-token-filename "api-token.txt")
(def ^:dynamic *api-token* nil)

(defn get-user-dm-id
  "get the direct message channel id for this user.
  open the dm channel if it hasn't been opened yet."
  [user-id]
  (if-let [dm-id (state/user-id->dm-id user-id)]
    dm-id
    (web/im-open *api-token* user-id)))


(defn dispatch-handle-slack-event [event] ((juxt :type :subtype) event))

(defmulti handle-slack-event #'dispatch-handle-slack-event)

(defmethod handle-slack-event ["message" nil]
  [{user-id :user, :as msg}]
  (when (not (state/bot? user-id))
    (handle-message msg)))

(defmethod handle-slack-event ["channel_joined" nil]
  [event]
  nil)

(defmethod handle-slack-event :default
  [event]
  nil)

(def heartbeating (atom false))

(def heartbeat-loop (atom nil))

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
  ()
  (stop-heartbeat)
  (slack/disconnect)
  (println "...pomodoro dying"))


(defn stop []
  (shutdown-app))

(defn start
  ([]
   (start (store/read-api-token api-token-filename)))
  ([api-token]
   (try
     (event/initialize-events)
     (restaurant/initialize-restaurants)
     (alter-var-root (var *api-token*) (constantly api-token))
     (slack/connect *api-token* try-handle-slack-event)
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
