(ns pomodoro.core
  (:gen-class)
  (:require
    [clj-slack-client
     [core :as slack]
     [rtm-transmit :as tx]
     [team-state :as state]
     [web :as web]]
    [pomodoro.file-ops :as store]
    [clj-time.core :as time]
    [clojure.string :as string]
    [org.httpkit.client :as http]))


(def api-token-filename "api-token.txt")
(def ^:dynamic *api-token* nil)

;(defn get-lunch-channel-id [] (:id (state/name->channel lunch-channel-name)))

(defn get-user-dm-id
  "get the direct message channel id for this user.
  open the dm channel if it hasn't been opened yet."
  [user-id]
  (if-let [dm-id (state/user-id->dm-id user-id)]
    dm-id
    (web/im-open *api-token* user-id)))

(defn contextualize-command
  "apply slack message context to the raw command"
  [cmd {requestor :user, text :text, ts :ts, channel-id :channel, :as msg} cmd-text]
  (-> cmd
      (assoc :requestor requestor)
      (assoc :text text)
      (assoc :cmd-text cmd-text)
      (assoc :channel-id channel-id)
      (assoc :ts ts)))

(defn printex
  [msg ex]
  (let [line (apply str (repeat 100 "-"))]
    (println line)
    (println msg)
    (println ex)
    (clojure.stacktrace/print-stack-trace ex)
    (println line)))

(defn get-command-signature-re []
  "returns a regex that will match a command signature, indicating
  that the user wants lunchbot to interpret the message as a command"
  (let [linkified-self (tx/linkify (state/self-id))]
    (re-pattern (str linkified-self ":?"))))


(defn message->command-text
  "determines if the message should be interpreted as a command, and if so, returns
  the command text from the message."
  [channel-id text]
  (when text
    (let [cmd-signature-re (get-command-signature-re)
          has-cmd-signature (re-find cmd-signature-re text)]
      (when (or (state/dm? channel-id) has-cmd-signature)
        (-> text
            (string/replace cmd-signature-re "")
            (string/trim))))))

(defn handle-command
  "translates the command into events, commits the events to the stream,
  handles the events, and returns replies."
  [cmd]
  ;(println cmd)
  cmd)

; to be used later with a more clever solution to getting the proper keyword
(def command-finding-functions
  {:start #(and (re-find #"zen" %) (re-find #"start" %))
   :end #(and (re-find #"zen" %) (re-find #"end" %))
   :unrecognized #(and %)})

(defn parse-command
  "Set the command type based on the input text"
  [input]
  (hash-map :command-type (if (re-find #"zen" input)
                            (if (or (re-find #"start" input)
                                    (re-find #"end" input))
                              (if (re-find #"start" input)
                                :start
                                :end)
                              :unrecognized)
                            :unrecognized)))

(def responses
  {:start "Focus for 26 minutes."
   :end "I've ended your timer."
   :unrecognized "I didn't catch that"})

(defn formulate-response
  "Give a string response to a command map and start async functions"
  [command channel-id]
  (let [cmd (:command-type command)
        reply (cmd responses)]
    (when (= cmd :start)
      (future (Thread/sleep 10000)
              (tx/say-message channel-id "Your timer has ended")))
    reply))

(defn handle-message
  "translates a slack message into a command, handles that command, and communicates the reply"
  [{channel-id :channel, text :text, :as msg}]
  (try
    (when-let [cmd-text (message->command-text channel-id text)]
      (let [raw-cmd (parse-command cmd-text); {:command-type :unrecognized}
            cmd (contextualize-command raw-cmd msg cmd-text)
            reply (formulate-response cmd channel-id)]
        (tx/say-message channel-id reply)))
    (catch Exception ex
      (printex (str "Exception trying to handle slack message\n" (str msg) ".") ex)
      (try (tx/say-message channel-id "@!#?@!")))))

(comment
  (defn handle-message
    "translates a slack message into a command, handles that command, and communicates the reply"
    [{channel-id :channel, text :text, :as msg}]
    (try
      (when-let [cmd-text (command/message->command-text channel-id text)]
        (let [raw-cmd (command/command-text->command cmd-text)
              cmd (contextualize-command raw-cmd msg cmd-text)
              replies (handle-command cmd)]
          (doseq [reply replies]
            (distribute-message reply))))
      (catch Exception ex
        (printex (str "Exception trying to handle slack message\n" (str msg) ".") ex)
        (try (talk/say-message channel-id "@!#?@!")))))
)


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

(defn try-handle-slack-event
  [event]
  (try
    (handle-slack-event event)
    (catch Exception ex
      (printex (str "Exception trying to handle slack event\n" (str event) ".") ex))))

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
