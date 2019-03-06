(ns keycloak.admin-test
  (:require [keycloak.admin :refer :all]
            [keycloak.deployment :refer :all]
            [clojure.test :as t]))

(def kc-admin3 (keycloak-client (client-conf "master" "admin-cli" "http://localhost:8080/auth") "admin" "password"));OK

(def kc-admin4 (keycloak-client (client-conf "master" "mybackend" "http://localhost:8080/auth" nil) "10337ce2-8dee-44a9-80b2-bc08ff8fc695"))

(create-realm! kc-admin3 "test11" "base")

