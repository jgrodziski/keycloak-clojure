(ns keycloak.authz-test
  (:require [keycloak.admin :refer :all]
            [keycloak.deployment :as deploy :refer [client-conf keycloak-client]]
            [keycloak.authz :as authz]
            [clojure.test :as t]))

(def admin-login "admin")
(def admin-password "secretadmin")
(def auth-server-url "http://localhost:8090/")

(def integration-test-conf
  (deploy/client-conf auth-server-url "master" "admin-cli"))

(def admin-client (deploy/keycloak-client integration-test-conf admin-login admin-password))

(comment 
  (def kc-admin (deploy/keycloak-client
                 (deploy/client-conf "master" "mybackend" "http://localhost:8080/auth")
                 "10337ce2-8dee-44a9-80b2-bc08ff8fc695"))

  (def kc-authz (authz/authz-client (deploy/client-conf-input-stream "master" "mybackend" "http://localhost:8080/auth" "10337ce2-8dee-44a9-80b2-bc08ff8fc695")))

  (authz/create-resource kc-authz "My resource" "urn:my-authz:resources:my-resource" ["urn:my-authz:scopes:access"])

  (authz/create-role-policy kc-admin "master" "mybackend" "testrole" "urn:my-authz:resources:my-resource" ["urn:my-authz:scopes:access"] ))
