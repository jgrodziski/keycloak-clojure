(ns keycloak.deployment-test
  (:require
   [clojure.test :as t :refer [deftest testing is]]
   [clojure.tools.logging :as log]
   [keycloak.deployment :as deployment :refer [deployment client-conf deployment-for-realms verify extract]]
   [keycloak.bean :as bean]
   [keycloak.admin :refer :all]
   [keycloak.authn :as authn :refer [authenticate access-token]]
   [keycloak.user :as user :refer [delete-and-create-user!]]
   ))

(def admin-login "admin")
(def admin-password "secretadmin")
(def auth-server-url "http://localhost:8090/")
;(def auth-server-url "http://login.default.minikube.devmachine")

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



(def admin-login "admin")
(def admin-password "secretadmin")
(def auth-server-url "http://localhost:8090/auth")

(def integration-test-conf
  (deployment/client-conf auth-server-url "master" "admin-cli"))

(deftest ^:integration deployment-test
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
                    user (user/delete-and-create-user! admin-client realm-name {:username username :password password})]
                (is (= username (.getUsername user)))
                (testing "authentication and token verification and extraction"
                  (let [token (authenticate auth-server-url realm-name client-id username password)
                        access-token (verify deployments realm-name (:access_token token))
                        extracted-token (extract access-token)]
                    (clojure.pprint/pprint extracted-token)
                    (is (= username (:username extracted-token)))))))))
        (testing "realm deletion"
          (delete-realm! admin-client realm-name)
          (is (thrown? javax.ws.rs.NotFoundException (get-realm admin-client realm-name))))))))

(defn delete-realms-except [realms-to-keep]
  (let [admin-client (deployment/keycloak-client integration-test-conf admin-login admin-password)
        realms (list-realms admin-client)]
    (doseq [realm realms]
      (when (not ((set realms-to-keep) (.getId realm)))
        (delete-realm! admin-client (.getId realm))))))
