(ns keycloak.user-test
  (:require
   [clojure.test :as test :refer [deftest testing is]]
   [clojure.tools.logging :as log]
   [clojure.java.shell :as shell]

   [testit.core :refer [fact facts =>]]

   [keycloak.deployment :as deployment :refer [deployment client-conf deployment-for-realms verify extract]]
   [keycloak.bean :as bean]
   [keycloak.admin :refer :all]
   [keycloak.authn :as authn :refer [authenticate access-token]]
   [keycloak.user :as user :refer [delete-and-create-user!]]
   ))


(def admin-login "admin")
(def admin-password "secretadmin")
;(def auth-server-url "http://login.default.minikube.devmachine")

(defn minikube-keycloak-service []
  (str (clojure.string/replace (:out (shell/sh  "minikube" "service" "--url" "keycloak-service")) "\n" "")
       "/auth"))

(def auth-server-url (minikube-keycloak-service))
;(def auth-server-url "http://localhost:8090/auth")


(def integration-test-conf (deployment/client-conf auth-server-url "master" "admin-cli"))
(def admin-client (deployment/keycloak-client integration-test-conf admin-login admin-password))

(def keycloak-deployment (deployment (client-conf
                                      {:auth-server-url  auth-server-url
                                       :admin-realm      "master"
                                       :realm            "electre-devmachine"
                                       :admin-username   "admin"
                                       :admin-password   "secretadmin"
                                       :client-admin-cli "admin-cli"
                                       :client-id        "bo-backend"
                                       :client-secret    "bc8205af-c056-4be6-97e0-9edc8e2c0eb3"})))

(deftest ^:integration user-creation-test
  (let [admin-client (deployment/keycloak-client integration-test-conf admin-login admin-password)]
    (testing "realm creation "
      (let [realm-name (str "test-realm-" (rand-int 1000))
            realm (create-realm! admin-client {:name realm-name
                                               :themes {:defaultLocale "fr",
                                                        :emailTheme "keycloak",
                                                        :internationalizationEnabled true,
                                                        :adminTheme "keycloak",
                                                        :supportedLocales #{"en" "fr"},
                                                        :loginTheme "keycloak",
                                                        :accountTheme "keycloak"}
                                               :accessTokenLifespan (Integer. 2)})]
        (is (= realm-name (.getRealm realm)))
        (log/info "realm created")
        (testing "create a client, then a deployment for that client"
          (let [client-id (str "test-client-" (rand-int 1000))
                created-client (create-client! admin-client realm-name client-id true)
                deployments (deployment-for-realms admin-client auth-server-url client-id [realm-name])]
            (is (= client-id (.getClientId created-client)))
            (log/info "client created and deployments created")
            (testing "user creation in the realm then join to group"
              (let [username (str "user-" (rand-int 1000))
                    password (str "pass" (rand-int 100))
                    user (delete-and-create-user! admin-client realm-name {:username username :password password})]
                (is (= username (.getUsername user)))
                (testing "authentication and token verification and extraction"
                  (let [token (authenticate auth-server-url realm-name client-id username password)
                        access-token (verify deployments realm-name (:access_token token))
                        extracted-token (extract access-token)]
                    (is (= username (:username extracted-token)))))
                (testing "disable user then re-enable it"
                  (fact (.isEnabled (user/disable-user! admin-client realm-name username)) => false)
                  (fact (.isEnabled (user/enable-user! admin-client realm-name username)) => true))))))
        (testing "realm deletion"
          (delete-realm! admin-client realm-name)
          (is (thrown? javax.ws.rs.NotFoundException (get-realm admin-client realm-name)))
          )))))
