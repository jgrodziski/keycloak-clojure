(ns keycloak.admin-test
  (:require [keycloak.admin :refer :all]
            [keycloak.deployment :as deployment :refer [keycloak-client client-conf]]
            [clojure.test :as t :refer [deftest testing is]]))


(def admin-login "admin")
(def admin-password "secretadmin")
(def auth-server-url "http://localhost:8090/auth")

(def integration-test-conf
  (deployment/client-conf "master" "admin-cli" auth-server-url))

; (def deployments (deployment-for-realms kc-admin-client auth-server-url client-account-backend ["electre"]))

(deftest ^:integration admin-test
  (let [admin-client (deployment/keycloak-client integration-test-conf admin-login admin-password)]
    (testing "realm creation "
      (let [realm-name (str "keycloak-clojure-test-" (rand-int 1000))
            realm (create-realm! admin-client realm-name "base")]
        (is (= realm-name (.getRealm realm)))
        (testing "create a client, then a deployment for that client"
          (let [client-id (str "keycloak-clojure-test-client-" (rand-int 1000))
                test-client (create-client! admin-client realm-name client-id false)]
            (is (= client-id (.getClientId test-client)))))
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
                        user (create-user! admin-client realm-name user-name "pasword")
                        joined-group (add-user-to-group! admin-client realm-name (.getId subgroup) (.getId user))
                        members (get-group-members admin-client realm-name (.getId subgroup))]
                    (is (= user-name (.getUsername user)))
                    (is (some #(= (.getId user) (.getId %)) members))
                    ))))))
        (testing "realm deletion"
          (delete-realm! admin-client realm-name)
          (is (thrown? javax.ws.rs.NotFoundException (get-realm admin-client realm-name))))))))
