(ns keycloak.file
  (:require [clojure.java.io :as io]
            [me.raynes.fs :as fs]))

(defn relative-path [dir f]
  (let [filedir (.toString (fs/parent f))
        relative-dir (clojure.string/replace filedir dir "")]
    (if (fs/exists? relative-dir)
      relative-dir
      (subs relative-dir 1))))

(defn parse-path
          "Given a file return a map with following keys: dir root base name ext relative, nil if file doesn't exist"
          [f]
          (if (fs/exists? f)
            {:dir (.toString (fs/parent f))
             :root (.toString (last (fs/parents f)))
             :relative-to-user-dir (relative-path (System/getProperty "user.dir") f)
             :base (fs/base-name f)
             :name (fs/name f)
             :ext (fs/extension f)}
            (throw (ex-info (format "File %s doesn't exist" f) {:file f}))))

(defn name-starts-with? [file s]
  (let [{:keys [dir root base name ext]} (parse-path file)]
    (.startsWith base s)))

(defn name-ends-with? [file s]
  (let [{:keys [dir root base name ext]} (parse-path file)]
    (.endsWith base s)))

(defn name-match? [re file]
  (let [{:keys [dir root base name ext]} (parse-path file)]
    (re-find re base)))

(defn ext? [file s]
  (let [{:keys [dir root base name ext]} (parse-path file)]
    (.endsWith ext s)))

(defn file? [file]
  (.isFile file))

(defn list-files
  ([dir]
   (file-seq (io/file dir)))
  ([dir pred]
   (filter pred (list-files dir))))
