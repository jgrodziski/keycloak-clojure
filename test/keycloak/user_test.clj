(ns keycloak.user-test
  (:require
   [clojure.test :as test :refer [deftest testing is]]
   [clojure.tools.logging :as log]
   [clojure.java.shell :as shell]

   [testit.core :refer [fact facts => truthy falsey]]
   [sc.api :as sc]

   [keycloak.deployment :as deployment :refer [deployment client-conf deployment-for-realms verify extract]]
   [keycloak.bean :as bean]
   [keycloak.admin :refer :all]
   [keycloak.authn :as authn :refer [authenticate access-token]]
   [keycloak.user :as user :refer [delete-and-create-user!]]
   ))


(def admin-login "admin")
(def admin-password "secretadmin")
;(def auth-server-url "http://login.default.minikube.devmachine")

(defn minikube-keycloak-service-or-localhost []
  (let [{:keys [out exit]} (shell/sh  "minikube" "service" "--url" "keycloak-service")]
    (if (= 0 exit)
      (str (clojure.string/replace out "\n" "") "/auth")
      "http://localhost:8090/auth")))

(def auth-server-url (minikube-keycloak-service-or-localhost))

(def integration-test-conf (deployment/client-conf auth-server-url "master" "admin-cli"))
(def admin-client (deployment/keycloak-client integration-test-conf admin-login admin-password))


(def fake-user {:last-name "Carter", :email "bobcarter@acme.org" :group "Example", :realm-roles ["employee" "manager" "example-admin"], :password "secretstuff", :username "bcarter", :first-name "Bob", :attributes {"org-ref" ["Example"]}, :in-subgroups ["IT"]})
(def fake-user-2 {:last-name "Reagan", :email "ronalreagan@acme.org" :group "Example", :realm-roles ["employee" "manager" "example-admin"], :password "secretstuff", :username "rreagan", :first-name "Ronald", :attributes {"org-ref" ["Example"]}, :in-subgroups ["IT"]})


(deftest ^:integration user-id-testing
  (let [admin-client (deployment/keycloak-client integration-test-conf admin-login admin-password)]
    (testing "realm creation "
      (let [realm-name (str "test-realm-" (rand-int 1000))
            realm      (create-realm! admin-client {:name realm-name
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
        (testing "create two users with only username in the realm"
          (let [username1 "user-100"
                username2 "user-199"
                password  (str "pass"  (rand-int 100))
                user1     (create-user! admin-client realm-name username1 password)
                user2     (create-user! admin-client realm-name username2 password)
                user1-id  (user/user-id admin-client realm-name username1)
                user2-id  (user/user-id admin-client realm-name username2)
                user-id   (user/user-id admin-client realm-name "user")]
            (is (= username1 (.getUsername user1)))
            (is (= username2 (.getUsername user2)))
            (is (= (.getId user1) user1-id))
            (is (= (.getId user2) user2-id))
            (is (= nil user-id))))
        (testing "create two users with username, first and last name + email in the realm"
          (let [user1     (user/create-user! admin-client realm-name fake-user)
                user2     (user/create-user! admin-client realm-name fake-user-2)
                user1-id  (user/user-id admin-client realm-name (:username fake-user) (:first-name fake-user) (:last-name fake-user) (:email fake-user))
                user2-id  (user/user-id admin-client realm-name (:username fake-user-2) (:first-name fake-user-2) (:last-name fake-user-2) (:email fake-user-2))
                user-id   (user/user-id admin-client realm-name "bcart")]
            (is (= (:username fake-user) (.getUsername user1)))
            (is (= (:username fake-user-2) (.getUsername user2)))
            (is (= (.getId user1) user1-id))
            (is (= (.getId user2) user2-id))
            (is (= nil user-id))))
        (testing "realm deletion"
          (delete-realm! admin-client realm-name)
          (is (thrown? javax.ws.rs.NotFoundException (get-realm admin-client realm-name)))
          )))))

(deftest ^:integration user-test
  (let [admin-client (deployment/keycloak-client integration-test-conf admin-login admin-password)]
    (testing "realm creation "
      (let [realm-name (str "test-realm-" (rand-int 1000))
            realm      (create-realm! admin-client {:name realm-name
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
          (let [client-id      (str "test-client-" (rand-int 1000))
                created-client (create-client! admin-client realm-name client-id true)
                deployments    (deployment-for-realms admin-client auth-server-url client-id [realm-name])]
            (is (= client-id (.getClientId created-client)))
            (log/info "client created and deployments created")
            (testing "user creation in the realm then join to group"
              (let [username (str "user-" (rand-int 1000))
                    password (str "pass"  (rand-int 100))
                    user     (delete-and-create-user! admin-client realm-name {:username username :password password})]
                (is (= username (.getUsername user)))
                (testing "authentication and token verification and extraction"
                  (let [token           (authenticate auth-server-url realm-name client-id username password)
                        access-token    (verify deployments realm-name (:access_token token))
                        extracted-token (extract access-token)]
                    (is (= username (:username extracted-token)))))
                (testing "get user-id by exact match"
                  (let [username2 (str (rand-int 1000) "-user")
                        password2 (str "pass" (rand-int 100))
                        user2     (delete-and-create-user! admin-client realm-name {:username username2 :password password2})
                        user2-id  (user/user-id admin-client realm-name username2)]
                    ;(sc/spy)
                    )
                  )
                #_(testing "disable user then re-enable it"
                  (fact (.isEnabled (user/disable-user! admin-client realm-name username)) => false)
                  (fact (.isEnabled (user/enable-user! admin-client realm-name username)) => true))
                (testing "Update the user with password provided should be ok"
                  ;(sc/spy)
                  (let [updated-user (user/update-user! admin-client realm-name (.getId user) (merge fake-user {:username username}))]
                    (fact updated-user => truthy)
                    (fact (.getEmail updated-user) => (:email fake-user))))
                 (testing "Update the user with NO password provided should also be ok"
                  (let [updated-user (user/update-user! admin-client realm-name (.getId user) (dissoc (merge fake-user {:username username}) :password))]
                    (fact updated-user => truthy)))))))
        (testing "realm deletion"
          (delete-realm! admin-client realm-name)
          (is (thrown? javax.ws.rs.NotFoundException (get-realm admin-client realm-name)))
          )))))
