(ns pomodoro.file-ops
  (:require [clojure.string :as string]
            [clojure.java.io :as io]))

(defn slurp-filename
  [filename]
  (let [file (io/file filename)]
    (when (and (.exists file)
               (< 0 (.length file)))
      (slurp file))))

(defn read-api-token
  [filename]
  (if-let [raw-api-token (slurp-filename filename)]
    (string/trim raw-api-token)
    (do (spit filename "your-api-token")
        (println "put your api token in" filename))))
