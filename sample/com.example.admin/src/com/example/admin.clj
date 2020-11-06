(ns com.example.admin
  (:require [keycloak.admin :as admin :refer :all]
            [keycloak.starter :as starter :refer [init!]]
            [keycloak.user :as user]
            [keycloak.deployment :as deployment :refer [keycloak-client client-conf]]
            [talltale.core :as talltale :refer :all]
            [me.raynes.fs :as fs])
  (:gen-class))


(require '[keycloak.deployment
           :as deployment
           :refer [keycloak-client client-conf]])

(def kc-client
  (-> (client-conf {:auth-server-url "http://localhost:8090/auth"
                    :realm "master"
                    :client-id  "admin-cli"})
      (keycloak-client "admin" "secretadmin")))


(require '[keycloak.admin :as admin])
(admin/create-realm! kc-client "example-realm")

(admin/create-client! kc-client "example-realm" "myfrontend")
(admin/create-client! kc-client "example-realm" "mybackend")

(admin/create-role! kc-client "example-realm" "employee")
(admin/create-role! kc-client "example-realm" "manager")


(admin/create-user! kc-client "example-realm" "user1" "pwd1")
(admin/create-user! "example-realm" "user2" "pw2")

(require '[keycloak.user :as user])
(user/create-or-update-user! kc-client "example-realm" {:username "bcarter" :first-name "Bob" :last-name "Carter" :password "abcdefg" :email "bcarter@example.com"} ["employee" "manager"] nil)

(user/add-realm-roles! kc-client "example-realm" "bcarter" ["manager"])

(admin/create-group! kc-client "example-realm" "mygroup")
(admin/add-username-to-group-name! kc-client "example-realm" "mygroup" "bcarter")
