(ns build
  (:require [clojure.tools.build.api :as b]))


(def lib 'keycloak-clojure)
(def version )
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"} ))
(def uber-file "target/keycloak-clojure.jar")

(defn uber [_]
  (b/delete {:path "target/uberjar"})
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/compile-clj {:basis basis
                  :ns-compilte ' [keycloak.starter]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis basis
           :main 'keycloak.starter
           :exclude [".*LICENSE.*.txt"]})

  )
