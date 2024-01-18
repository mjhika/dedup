(ns mjhika.dedup.files
  (:require
   [babashka.fs :as fs]
   [clj-commons.digest :as digest]
   [taoensso.timbre :as timbre]))

(defn enumerate-files
  "Enumerates files of the directory."
  [dir pattern]
  (let [files (mapv fs/file (filter (complement fs/directory?) (fs/glob dir pattern)))]
    files))

(defn- file-type
  "Return the file type according to the extension."
  [s]
  (re-find #"\.\w+$" s))

(def ^:private hash-fn
  {:md5 digest/md5
   :sha-256 digest/sha-256
   :sha-512 digest/sha-512})

(defn hash-file
  "Takes a file with optional hasher and returns a hashed-file map."
  [f hasher]
  (let [file (fs/file f)
        file-path (str file)
        file-name (fs/file-name f)
        hashed-return     {:digest ((get hash-fn hasher) f)
                           :file-name file-name
                           :file-path file-path
                           :file-type (or (file-type file-path)
                                          "file")}]
    (timbre/info (str "Hashed: " file-name))
    hashed-return))
