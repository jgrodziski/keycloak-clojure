(ns keycloak.utils-test
  (:require [keycloak.utils :as sut]
            [clj-http.client :as http]
            [clojure.java.shell :as shell]
            [clojure.test :as t]))

(defn keycloak-localhost-8090-running? []
  (= 200 (:status (http/get "http://localhost:8090/auth/realms/master"))))

(defn minikube-keycloak-service-or-localhost []
  (if (keycloak-localhost-8090-running?)
      "http://localhost:8090/auth")
 #_ (let [{:keys [out exit]} (shell/sh "bash" "-c" "kubectl get pod -l app=keycloak -o json | jq -r '.items[].status.phase'" )]
    (if (= "Running\n" out)
      (str (clojure.string/replace out "\n" "") "/auth")
      "http://localhost:8090/auth"))
  )
