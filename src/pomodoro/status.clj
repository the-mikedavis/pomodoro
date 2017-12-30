; get the status of Do Not Disturb
(ns pomodoro.status
  (:require [pomodoro
             [interact :as interact]
             [state :as state]]
            [clj-slack-client.dnd :as dnd]
            [clojure.string :as string]))

(defn tell-remaining-time
  "Generate a string with the time remaining on a Do Not Disturb"
  [msg response]
  (if (:dnd_enabled response)
    (let [t (dnd/time-delta (:ts msg) response)]
      (str "The pomodoro is running. It has " (:min t)
           " minutes and " (:sec t) " seconds remaining."))
    "No pomodoro is running."))

(defn get-user-dnd-status
  "Get the reply string of the time left on Do Not Disturb for the
  user in the message. The second arity is for if you already have
  the json response"
  [msg]
  (tell-remaining-time
    msg
    (dnd/get-user-dnd state/api-token
                      (:user msg))))

(defn get-team-dnd-status
  "Gets the reply string for the whole team's dnd status."
  [msg]
  (->> (dnd/get-team-dnd state/api-token)
       :users
       (map (fn [[user-id api-response]]
              (str (interact/get-name user-id)
                   ": "
                   (tell-remaining-time msg api-response))))
       (string/join "\n")))
