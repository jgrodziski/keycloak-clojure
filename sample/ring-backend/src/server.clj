(ns server
  (:require [ring.adapter.jetty :as jetty]
            [keycloak.deployment :as deployment :refer [keycloak-client client-conf]]))

(defn handler [request]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    "Hello World"})

(def server (jetty/run-jetty handler {:port 3000 :join? false}))

(def kc-client (-> (client-conf {:auth-server-url "http://localhost:8080" :realm "master" :client-id "admin-cli"})
                   (keycloak-client "admin" "password")))
