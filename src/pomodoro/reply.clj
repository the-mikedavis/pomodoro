(ns pomodoro.reply
  (:require [pomodoro
             [interact :as interact]
             [mutables :as mutes]
             [status :as status]]))

(defn cancel
  "Cancel a future if it's not nil"
  [fut]
  (when (not (nil? fut)) (future-cancel fut))
  nil)

; handle a command. The return must be a string which will be said
; directly after the user talks to pomo
(defmulti respond
  (fn [command msg]
    (:command-type command)))

(defn create-timer
  "Create a future which will serve a message after a given time."
  [t channel message]
  (future (Thread/sleep t)
          (interact/say channel message)))

; start the pomodoro
(defmethod respond :start
  [command msg]
  (swap!
    mutes/timer
    (fn [fut]
      (cancel fut)
      (create-timer 10000
                    (:channel msg)
                    "Your pomodoro has ended. Nice focus!")))
  "Your pomodoro has started. Please turn on Do Not Disturb.")

; end the pomodoro
(defmethod respond :end
  [command msg]
  (swap!
    mutes/timer
    cancel)
  "Pomodoro cancelled.")

; get the asker's status
(defmethod respond :status
  [command msg]
  (status/get-user-dnd-status msg))

; get the status of the team
(defmethod respond :team-status
  [command msg]
  (status/get-team-dnd-status msg))

; default an "unrecognized" commands
(defmethod respond :default
  [command msg]
  "Sorry, I didn't understand that")


; #"(?i)a" is ignore case
(def command-finding-functions
  {:start #(re-find #"start" %)
   :end #(re-find #"end" %)
   :status #(re-find #"status" %)
   :team-status #(re-find #"team" %)
   :unrecognized #(and %)})

(defn parse-command
  "Extract the command keyword from the raw message text"
  [input]
  (hash-map
    :command-type
    ((comp first first) (filter (fn [[k v]] (v input))
                                command-finding-functions))))

(defn contextualize-command
  "Give the slack message context to a raw command"
  [cmd {requestor :user, text :text, ts :ts, channel-id :channel-id, :as msg}
   command-text]
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
                             (str msg) ".") ex)
      (try (interact/say channel-id "@!#?@!")))))
