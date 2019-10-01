(ns keycloak.admin-test
  (:require [keycloak.admin :refer :all]
            [keycloak.user :as user]
            [keycloak.deployment :as deployment :refer [keycloak-client client-conf]]
            [clojure.test :as t :refer [deftest testing is]]))


(def admin-login "admin")
(def admin-password "secretadmin")
(def auth-server-url "http://localhost:8090/auth")

(def integration-test-conf
  (deployment/client-conf auth-server-url "master" "admin-cli"))

; (def deployments (deployment-for-realms kc-admin-client auth-server-url client-account-backend ["electre"]))

(def admin-client (deployment/keycloak-client integration-test-conf admin-login admin-password))

(deftest ^:integration admin-test
  (let [admin-client (deployment/keycloak-client integration-test-conf admin-login admin-password)]
    (testing "realm creation "
      (let [realm-name (str "keycloak-clojure-test-" (rand-int 1000))
            realm (create-realm! admin-client realm-name "base")]
        (is (= realm-name (.getRealm realm)))
        (testing "create a client, then a deployment for that client"
          (let [confid-client-id (str "keycloak-clojure-config-client-" (rand-int 1000))
                public-client-id (str "keycloak-clojure-public-client-" (rand-int 1000))
                public-client-id-2 (str "frontend-client-" (rand-int 1000))
                public-client-2 (create-client! admin-client realm-name (client public-client-id-2 true ["http://localhost:3000/*"] ["http://localhost:3000"]))
                confid-client (create-client! admin-client realm-name confid-client-id false)
                public-client (create-client! admin-client realm-name public-client-id true)]
            (is (= confid-client-id (.getClientId confid-client)))
            (is (= public-client-id (.getClientId public-client)))
            (is (= public-client-id-2 (.getClientId public-client-2)))
            ))
        (testing "create a role in that realm"
          (let [role (create-role! admin-client realm-name "employee")
                roles (list-roles admin-client realm-name)]
            (is (> (count roles) 0))
            (is (not (nil? (get-role admin-client realm-name "employee"))))))
        (testing (str "group creation in the realm" realm-name)
          (let [group-name (str "group-" (rand-int 1000))
                group (create-group! admin-client realm-name group-name)]
            (is (= group-name (.getName group)))
            (testing "subgroup creation"
              (let [subgroup-name (str "subgroup-" (rand-int 1000))
                    subgroup (create-subgroup! admin-client realm-name (.getId group) subgroup-name)]
                (is (= subgroup-name (.getName subgroup)))
                (testing "user creation in the realm then join to group"
                  (let [user-name (str "user-" (rand-int 1000))
                        user (create-user! admin-client realm-name user-name "password")
                        joined-group (add-user-to-group! admin-client realm-name (.getId subgroup) (.getId user))
                        members (get-group-members admin-client realm-name (.getId subgroup))]
                    (is (= user-name (.getUsername user)))
                    (is (some #(= (.getId user) (.getId %)) members))
                    (user/delete-user! admin-client realm-name (.getId user))))))))
        (testing "realm deletion"
          (delete-realm! admin-client realm-name)
          (is (thrown? javax.ws.rs.NotFoundException (get-realm admin-client realm-name))))))))

(deftest ^:integration test-creation-user-with-client-roles
  (let [admin-client (deployment/keycloak-client integration-test-conf admin-login admin-password)
        realm-name (str "keycloak-clojure-test-" (rand-int 1000))
        realm (create-realm! admin-client realm-name "base")]
    (testing "create a user with client roles"
      (let [user-name (str "user-" (rand-int 1000))
            user (user/create-or-update-user! admin-client "master"
                                              {:username "testuser" :password "password"}
                                              nil
                                              {(str realm-name "-realm") ["impersonation"]})]
        (prn user)
        ))
    ))
