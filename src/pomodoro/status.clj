; get the status of pomodoro timers
(ns pomodoro.status
  (:require [pomodoro
             [interact :as interact]
             [mutables :as mutes]]
            [clojure.string :as string]))

(defn parse-int [s]
  "Convert a string to integer"
  (Integer. (re-find #"\d+" s)))

(defn get-time
  "Gets the time in minutes between two time stamps. Assumes that start
  is a string and end is an integer."
  [end start]
  (quot (- end (parse-int start)) 60))

(defn get-dnd-time
  "Return the number of minutes left on a Do Not Disturb. Compares the
  time stamps of the message recieved and the response of the
  dnd.info call"
  [msg response]
  (get-time (:next_dnd_end_ts response)
            (:ts msg)))

(defn tell-dnd-time
  "Generate a string with the time remaining on a Do Not Disturb"
  [msg response]
  (if (:dnd_enabled response)
    (str "The pomodoro is running. It has "
         (get-dnd-time msg response)
         " minutes remaining.")
    "No pomodoro is running."))

(defn get-dnd-info
  "Get the time left on Do Not Disturb for the user in the message"
  ([msg user] ; single person
   (->> {:token mutes/*api-token* :user user}
        (interact/call-slack-web-api "dnd.info")
        (interact/get-api-response)))
  ([msg]      ; the team
   (->> {:token mutes/*api-token*}
        (interact/call-slack-web-api "dnd.teamInfo")
        (interact/get-api-response))))

(defn get-user-dnd-status
  "Get the reply string of the time left on Do Not Disturb for the
  user in the message. The second arity is for if you already have
  the json response"
  [msg]
   (tell-dnd-time msg (get-dnd-info msg (:user msg))))


(defn get-team-dnd-status
  "Gets the reply string for the whole team's dnd status."
  [msg]
  (let [resp (get-dnd-info msg)]
    (->> (:users resp)
         (map (fn [[k v]] ; k is a user ID, v is the response about them
                (str (interact/get-name k)
                     ": "
                     (tell-dnd-time msg v))))
         (string/join "\n"))))
