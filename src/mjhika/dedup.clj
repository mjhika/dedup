(ns mjhika.dedup
  (:gen-class)
  (:require
   [babashka.cli :as cli]
   [clojure.core.async :as async]
   [clojure.java.io :as io]
   [clojure.pprint :refer [pprint]]
   [mjhika.dedup.database :as database]
   [mjhika.dedup.files :as files]
   [taoensso.timbre :as timbre]))

(set! *warn-on-reflection* true)

(def ^:private cli-schema
  {:coerce {:dir [:string]
            :glob :string
            :hasher :keyword
            :threads :long
            :save :boolean}
   :alias {:r :dir
           :g :glob
           :h :hasher
           :t :threads
           :s :save}
   :require [:dir]
   :args->opts (repeat :dir)})

(defn- when-many? [[digest paths]]
  (when (> (count paths) 1)
    [digest paths]))

(defn- print-to-disk? [bool data]
  (if bool
    (pprint data (io/writer "dedup-output.txt"))
    (do (timbre/info "Results are in: ")
        (pprint data))))

(defn -main
  "-main has four main actions.
  1. setup the initial let bindings for most of the rest of the execution and 
     file enumeration
  2. kickoff the pipeline to hash all files found from the supplied dir and glob
  3. consume all the hashed results and transact them into the db
  4. query the database keeping only the digests with more than 1 file path and 
     either printing to the console or saving to disk."
  [& opts]
  (let [{:keys [dir
                glob
                hasher
                threads
                save]
         :or {glob "**"
              hasher :sha-256
              threads 10
              save false}} (cli/parse-opts opts cli-schema)
        files (files/enumerate-files (first dir) glob)
        xf (map #(files/hash-file % hasher))
        hashed (async/chan)
        conn database/conn]

    (async/pipeline-blocking threads
                             hashed
                             xf
                             (async/to-chan! files))

    (doseq [data (async/<!! (async/into [] hashed))]
      (database/create-file-entry data conn))

    (let [db @conn]
      (print-to-disk? save
                      (->> (database/get-digests db)
                           (map #(database/get-file-paths % db))
                           (keep when-many?)
                           vec
                           (into {}))))))
