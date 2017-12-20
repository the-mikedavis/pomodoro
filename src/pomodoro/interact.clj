(ns pomodoro.interact
  (:require [pomodoro
             [mutables :as mutes]]
            [clj-slack-client
             [rtm-transmit :as tx]
             [web :as web]
             [team-state :as state]]
            [org.httpkit.client :as http]
            [cheshire.core :as json]
            [clojure.string :as string]))

; these methods written by Tony van Riet
(def slack-api-base-url "https://slack.com/api")

(defn get-api-response
  "Takes a full http response map and returns the api response as a map."
  [http-response]
  (let [response-body-bytes (:body http-response)
        response-body-json (byte-streams/to-string response-body-bytes)
        api-response (json/parse-string response-body-json true)]
    api-response))

(defn call-slack-web-api
  "Call any method from the slack API"
  ([method-name]
   (call-slack-web-api method-name {}))
  ([method-name params]
   (let [method-url-base (str slack-api-base-url "/" method-name)]
     @(http/post method-url-base {:query-params params}))))

(defn get-user-dm-id
  "get the direct message channel id for this user.
  open the dm channel if it hasn't been opened yet."
  [user-id]
  (if-let [dm-id (state/user-id->dm-id user-id)]
    dm-id
    (web/im-open mutes/*api-token* user-id)))

(defn printex
  "Print an exception to CLI with stacktrace"
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

; end

(defn say
  "Send a message to a channel"
  [channel-id message]
  (tx/say-message channel-id message))

(defn get-name
  "Get the name of a person from their id"
  [id]
  ((comp :real_name :user)
   (web/call-and-get-response 
     "users.info"
     {:token mutes/*api-token* :user (name id)})))

(defn get-bot-self-id-regex []
  "Creates a regex to match the bot's ID"
  (-> (state/self-id)
      (tx/linkify)
      (str)
      (re-pattern)))
