(ns pomodoro.reply
  (:require [pomodoro
             [interact :as interact]
             [state :as state]
             [status :as status]]))

; number of ms a timer should last
(def timer-time 10000) ; 10 seconds, for development

(defn cancel
  "Cancel a future if it is a future."
  [fut]
  (when (future? fut) (future-cancel fut))
  nil)


(defn create-timer
  "Create a future which will serve a message after a given time."
  [t channel message]
  (future (Thread/sleep t)
          (interact/say channel message)))


; handle a command. The return must be a string which will be said
; directly after the user talks to pomo
(defmulti respond
  (fn [command msg]
    (:command-type command)))

; start the pomodoro
(defmethod respond :start
  [_ msg]
  (swap! state/timer
         (fn [fut]
           (cancel fut)
           (create-timer timer-time (:channel msg)
                         "Your pomodoro has ended. Nice focus!")))
  "Your pomodoro has started. Please turn on Do Not Disturb.")

; end the pomodoro
(defmethod respond :end
  [_ _]
  (if (future? @state/timer)
    (do (swap! state/timer cancel)
        "Pomodoro cancelled.")
    "The timer isn't on."))

; get the asker's status
(defmethod respond :status
  [_ msg]
  (status/get-user-dnd-status msg))

; get the status of the team
(defmethod respond :team-status
  [_ msg]
  (status/get-team-dnd-status msg))

; default and "unrecognized" commands
; TODO: print a help dialog
(defmethod respond :default
  [_ _]
  "Sorry, I didn't understand that")

; a series of regexes to pull out the keyword
(def command-finding-functions
  {#(re-find #"(?i)start" %) :start
   #(re-find #"(?i)end" %) :end
   #(re-find #"(?i)status" %) :status
   #(re-find #"(?i)team" %) :team-status
   #(and %) :unrecognized}) ; returns true always

(defn parse-command
  "Extract a command's keyword from the raw message text"
  [input]
  (hash-map
    :command-type
    (->> command-finding-functions
         (filter (fn [[k v]] (k input)))
         first second)))


(defn contextualize-command
  "Give the slack message context to a command"
  [cmd {requestor :user, text :text, ts :ts,
        channel-id :channel-id, :as msg} command-text]
  (-> cmd
      (assoc :requestor requestor)
      (assoc :text text)
      (assoc :cmd-text command-text)
      (assoc :channel-id channel-id)
      (assoc :ts ts)))


(defn handle-message
  "Reads in a message, parses it as a command, executes responses, and
  sends a reply"
  [{channel-id :channel, text :text, :as msg}]
  (try
    (when-let [command-text (interact/message->command-text
                              channel-id text)]
      (let [raw-command (parse-command command-text)
            command (contextualize-command raw-command msg command-text)
            reply (respond command msg)]
        (interact/say channel-id reply)))
    (catch Exception ex
      (interact/printex (str "Exception trying to handle slack message\n"
                             (str msg) "\n") ex)
      (try (interact/say channel-id "@!#?@!")))))
