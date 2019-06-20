(ns keycloak.admin-test
  (:require [keycloak.admin :refer :all]
            [keycloak.deployment :refer :all]
            [clojure.test :as t :refer [deftest testing is]]))


(def admin-login "admin")
(def admin-password "secretadmin")
(def integration-test-conf
  (client-conf "master" "admin-cli" "http://localhost:8090/auth"))

(comment (def kc-admin4 (keycloak-client (client-conf "master" "mybackend" "http://localhost:8080/auth" nil) "10337ce2-8dee-44a9-80b2-bc08ff8fc695"))
        (create-realm! kc-admin3 "test11" "base"))

(deftest admin-test
  (let [admin-client (keycloak-client integration-test-conf admin-login admin-password)]
    (testing "realm creation then deletion"
      (let [realm-name (str "keycloak-clojure-test" (rand-int 1000))
            realm (create-realm! admin-client realm-name "base")]
        (is (= realm-name (.getRealm realm)))
        (delete-realm! admin-client realm-name)))
    ))
