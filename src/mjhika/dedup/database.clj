(ns mjhika.dedup.database
  (:require
   [datascript.core :as d]))

(def ^:private schema
  {:dedup/digest {:db/cardinality :db.cardinality/one}
   :dedup/file-name {:db/cardinality :db.cardinality/one}
   :dedup/file-path {:db/cardinality :db.cardinality/many
                     :db/unique :db.unique/identity}
   :dedup/file-type {:db/cardinality :db.cardinality/one}})

(def conn (d/create-conn schema))

(defn create-file-entry
  "Takes the digested file and transacts it's values."
  [{:keys [digest file-path file-type file-name]} conn]
  (d/transact! conn [{:db/id -1
                      :dedup/digest digest
                      :dedup/file-name file-name
                      :dedup/file-path file-path
                      :dedup/file-type file-type}]))

(defn get-digests [db]
  (apply concat
         (d/q '[:find ?digest
                :where
                [?e :dedup/digest ?digest]]
              db)))

(defn get-file-paths [digest db]
  [digest (apply concat
                 (d/q `[:find ?file-path
                        :where
                        [?e :dedup/digest ~digest]
                        [?e :dedup/file-path ?file-path]]
                      db))])
