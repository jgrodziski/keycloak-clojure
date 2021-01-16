(ns ^:no-doc  keycloak.doc
  (:require [keycloak.meta :as meta]
            [codox.main :as codox]
            [keycloak.file :as file :refer [file?]]
            [keycloak.s3 :as s3]))

(defn generate-docs []
  (codox/generate-docs
   {:name "keycloak-clojure"
    :project {:name "Keycloak-Clojure",
              :version meta/version,
              :logo "http://www.keycloak-clojure.org/keycloak-plus-clojure.png"
              :description "N/A"
              :license {:name "MIT License" :url "https://github.com/jgrodziski/keycloak-clojure/blob/master/LICENSE"}}
    :version meta/version
    :logo "http://keycloak-clojure.org/keycloak-plus-clojure.png"
    :license {:name "MIT License" :url "https://github.com/jgrodziski/keycloak-clojure/blob/master/LICENSE"}
    :description "Keycloak library for Clojure

"
    :language :clojure
    :source-paths ["src"]
    ;root-path (System/getProperty "user.dir")
    ;:root-path "~/Dropbox/projects/keycloak-clojure"
    :output-path "doc/codox"
    ;:source-uri "https://www.github.com/jgrodziski/keycloak-clojure/blob/{git-commit}/{filepath}#L{line}"
    :source-uri "https://github.com/foo/bar/blob/{version}/#L{line}"
    :metadata {:doc/format :markdown :doc ""}
    :doc-files ["doc/docs/concepts.md" "doc/docs/setup.md" "doc/docs/admin.md" "doc/docs/frontend.md" "doc/docs/backend.md" "doc/docs/howtos.md"]
    :themes [:rdash]}))

(System/setProperty "AWS_REGION" "us-east-1")

(defn upload-dir
  ([dir pred bucket]
   (doseq [file (file/list-files dir pred)]
     (s3/put-object bucket dir file)))
  ([dir bucket]
   (upload-dir dir file? bucket)))

(defn upload-docs []
  ;(s3/create-bucket BUCKET REGION)
  (upload-dir "doc/codox" "keycloak-clojure.org")
  (upload-dir "." #(file/name-ends-with? % ".png")  "keycloak-clojure.org")
  (upload-dir "resources" #(file/name-ends-with? % ".png")  "keycloak-clojure.org"))

(defn -main [& args]
  (println "Generate Keycloak-Clojure documentation for version " meta/version)
  (generate-docs)
  (upload-docs)
  )


