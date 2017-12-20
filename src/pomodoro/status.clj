; get the status of Do Not Disturb
(ns pomodoro.status
  (:require [pomodoro.interact :as interact]
            [clojure.string :as string]))

(defn parse-int [s]
  "Convert a string to integer"
  (Integer. (re-find #"\d+" s)))

(defn time-difference
  "Gets the time in minutes between two time stamps. Assumes that start
  is a string and end is an integer."
  [end start]
  (quot (- end (parse-int start)) 60))

(defn get-remaining-time
  "Return the number of minutes left on a Do Not Disturb. Compares the
  time stamps of the message recieved and the response of the
  dnd.info call"
  [msg response]
  (time-difference (:next_dnd_end_ts response)
            (:ts msg)))

(defn tell-remaining-time
  "Generate a string with the time remaining on a Do Not Disturb"
  [msg response]
  (if (:dnd_enabled response)
    (str "The pomodoro is running. It has "
         (get-remaining-time msg response)
         " minutes remaining.")
    "No pomodoro is running."))

(defn get-dnd-response
  "Get the time left on Do Not Disturb for the user in the message"
  ([msg user] ; single person
   (interact/call-and-get-response "dnd.info"
                                   {:user user}))
  ([msg]      ; the team
   (interact/call-and-get-response "dnd.teamInfo")))

(defn get-user-dnd-status
  "Get the reply string of the time left on Do Not Disturb for the
  user in the message. The second arity is for if you already have
  the json response"
  [msg]
   (tell-remaining-time msg (get-dnd-response msg (:user msg))))


(defn get-team-dnd-status
  "Gets the reply string for the whole team's dnd status."
  [msg]
  (let [resp (get-dnd-response msg)]
    (->> (:users resp)
         (map (fn [[user-id api-response]]
                (str (interact/get-name user-id)
                     ": "
                     (tell-remaining-time msg api-response))))
         (string/join "\n"))))
