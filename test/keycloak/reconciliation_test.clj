(ns keycloak.reconciliation-test
  (:require
   [clojure.test :as t :refer :all]
   [clojure.tools.logging :as log :refer [info]]

   [keycloak.admin :as admin]
   [keycloak.user :as user]
   [keycloak.deployment :as deployment]
   [keycloak.reconciliation :refer :all]
   [keycloak.utils-test :refer [minikube-keycloak-service-or-localhost]]
   [keycloak.utils :as utils]))

(def admin-login "admin")
(def admin-password "secretadmin")
(def auth-server-url (minikube-keycloak-service-or-localhost))

(def integration-test-conf (deployment/client-conf auth-server-url "master" "admin-cli"))

(deftest updates-test
  (testing "find items in current coll that are different from the ones in the desired coll"
    (let [desired [{:id 1 :content "yo"} {:id 2 :content "ya"} {:id 3 :content "yo"} {:id 4 :content "yo"}]
          current [{:id 1 :content "yo"} {:id 2 :content "yo"} {:id 3 :content "yo"}]]
      (is (empty? (find-differents :id current current)))
      (is (empty? (find-differents :id [] desired)))
      (is (empty? (find-differents :id current [])))
      (is (= [{:id 2 :content "ya"}] (find-differents :id current desired))))))

(deftest deletions-test
  (testing "find items in current coll missing from the desired coll"
    (let [desired [{:id 1 :content "yo"} {:id 2 :content "yo"}]
          current [{:id 1 :content "yo"} {:id 2 :content "yo"} {:id 3 :content "yo"}]]
      (is (empty? (find-deletions :id [] desired)))
      (is (= current (find-deletions :id current [])))
      (is (empty? (find-deletions :id current current)))
      (is (= [{:id 3 :content "yo"}] (find-deletions :id current desired))))))

(deftest additions-test
  (testing "find items in desired coll missing from the current coll"
    (let [desired [{:id 1 :content "yo"} {:id 2 :content "yo"} {:id 3 :content "yo"}]
          current [{:id 1 :content "yo"} {:id 2 :content "yo"}]]
      (is (empty? (find-additions :id current current)))
      (is (= desired (find-additions :id [] desired)))
      (is (empty? (find-additions :id current [])))
      (is (= [{:id 3 :content "yo"}] (find-additions :id current desired))))))

(defn generate-and-create-users [keycloak-client realm-name roles n]
  (doall (for [i (range n)]
           (let [user (user/generate-user)]
             (prn "create user " (:username user))
             (user/create-user! keycloak-client realm-name user)
             (user/set-realm-roles! keycloak-client realm-name (:username user) roles)
             user))))

(defn generate-and-create-users-with-usernames [keycloak-client realm-name roles usernames]
  (doall (for [username usernames]
           (let [user (user/generate-user username)]
             (prn "create user " (:username user))
             (user/create-user! keycloak-client realm-name user)
             (user/set-realm-roles! keycloak-client realm-name username roles)
             user))))

(deftest ^:integration make-plan-test
  (let [keycloak-client (deployment/keycloak-client integration-test-conf admin-login admin-password)]
    (testing "realm, roles and generated users creation for"
      (let [realm-name      (str "test-realm-" (rand-int 1000))
            realm           (admin/create-realm! keycloak-client {:name realm-name})
            roles           #{"role1" "role2" "role3" "role4"}
            _               (admin/create-roles! keycloak-client realm-name roles)
            _               (do  (log/info "realm created"))
            generated-users-1 (generate-and-create-users keycloak-client realm-name roles 2)
            generated-users-2 (concat generated-users-1 (generate-and-create-users-with-usernames keycloak-client realm-name roles #{"to-be-modified-1" "to-be-modified-2"}))
            generated-users-3 (concat generated-users-2 (generate-and-create-users-with-usernames keycloak-client realm-name roles #{"to-be-deleted-1"  "to-be-deleted-2"}))]
        (is (= realm-name (.getRealm realm)))
        ;(Thread/sleep 8000)
        ;(prn (user/get-users admin-client realm-name))
        (testing "make plan with additions"
          (let [user-in-addition (user/generate-user "to-be-added-1")
                plan (make-users-plan keycloak-client realm-name [user-in-addition])]
            (prn plan)
            (is (= 1 (count (:user/additions plan))))
            (is (= "to-be-added-1" (:username (first (:user/additions plan)))))))
        (testing "make plan with deletion"
          (let [plan (make-users-plan keycloak-client realm-name generated-users-2)]
            (is (= 2 (count (:user/deletions plan))))
            (is (= "to-be-deleted-1" (:username (first (:user/deletions plan)))))
            (is (= "to-be-deleted-2" (:username (second (:user/deletions plan)))))))
        (testing "make plan with updates"
          (let [plan (make-users-plan keycloak-client realm-name [(user/generate-user "to-be-modified-1")])]
            (is (= 1 (count (:user/updates plan))))
            (is (= "to-be-modified-1" (:username (first (:user/updates plan)))))))
        (testing "realm deletion"
          (admin/delete-realm! keycloak-client realm-name)
          (is (thrown? javax.ws.rs.NotFoundException (admin/get-realm keycloak-client realm-name)))))))
  (testing "Fetch the existing users and diff the one that need update or creation"
    (let [])))

(def admin-client (deployment/keycloak-client integration-test-conf admin-login admin-password))
(let [admin-client (deployment/keycloak-client integration-test-conf admin-login admin-password)]
  (user/create-or-update-user! admin-client "test-realm-214" {:no-abonne nil, :email "", :last-name "MOLLAT", :group "mollat", :realm-roles ["api-alimentation" "api-champs-extras" "api-notice-org" "api-admin-perimetre-pilote-par-exclusion" "api-alimentation-init"], :password "6w1wdgXIoT5HSETWtJ963hxZH7NV6rHp", :username "api-mollat-gestion", :first-name "", :no-affaire-SF nil, :attributes {"org-ref" ["mollat"] "test2" ["yo"] "managed-accounts" ["api-mollat-web" "api-mollat-pro" "yo"]}, :in-subgroups ["it"]} nil nil)
  (user/create-or-update-user! admin-client "test-realm-214" {:username "fake-user-311" :first-name "Ashley" :last-name "Fernandez" :email "fake-user-311@me.com" :attributes {"test" ["yo"]} :password "password"} nil nil))



(deftest ^:integration apply-plan-test
  (let [admin-client (deployment/keycloak-client integration-test-conf admin-login admin-password)]
    (testing "realm, roles and generated users creation for"
      (let [realm-name      (str "test-realm-" (rand-int 1000))
            realm           (admin/create-realm! admin-client {:name realm-name})
            roles           #{"role1" "role2" "role3" "role4"}
            _               (admin/create-roles! admin-client realm-name roles)
            _               (do  (log/info "realm created"))
            generated-users-1 (generate-and-create-users keycloak-client realm-name roles 2)
            generated-users-2 (concat generated-users-1 (generate-and-create-users-with-usernames keycloak-client realm-name roles #{"to-be-modified-1" "to-be-modified-2"}))
            generated-users-3 (concat generated-users-2 (generate-and-create-users-with-usernames keycloak-client realm-name roles #{"to-be-deleted-1"  "to-be-deleted-2"}))]
        (is (= realm-name (.getRealm realm)))
        (testing "make plan with additions"
          (let [user-in-addition (user/generate-user "to-be-added-1")
                desired-state    (conj generated-users-3 user-in-addition)
                plan             (make-users-plan admin-client realm-name desired-state)]
            (is (= 1 (count (:user/additions plan))))
            (is (= "to-be-added-1" (:username (first (:user/additions plan)))))
            (testing "Apply plan with additions"
              (let [report (apply-users-plan admin-client realm-name plan)
                    users  (utils/associate-by :username (user/get-users-beans admin-client realm-name))]
                (is (= 7 (count users)))
                (is (get users "to-be-added-1") user-in-addition)))
            ))
        (testing "make plan with deletions"
          (let [plan (make-users-plan admin-client realm-name generated-users-2)]
            (is (= 3 (count (:user/deletions plan))))
            (testing "Apply plan with deletions"
              (let [report (apply-users-plan admin-client realm-name plan)
                    users  (utils/associate-by :username (user/get-users-beans admin-client realm-name))]
                (is (= 4 (count users)))
                (is (nil? (get users "to-be-deleted-1")))
                (is (nil? (get users "to-be-deleted-2")))
                (is (nil? (get users "to-be-added-1")))))))
        (testing "make plan with updates"
          (let [to-be-modified-1 (user/generate-user "to-be-modified-1")
                to-be-modified-2 (user/generate-user "to-be-modified-2")
                plan (make-users-plan admin-client realm-name (conj generated-users-1
                                                                    to-be-modified-1
                                                                    to-be-modified-2))]
            (is (= 2 (count (:user/updates plan))))
            (is (= "to-be-modified-1" (:username (first (:user/updates plan)))))
            (prn plan)
            (testing "Apply plan with updates"
              (let [report (apply-users-plan admin-client realm-name plan)
                    users  (utils/associate-by :username (user/get-users-beans admin-client realm-name))
                    ]
                (is (= 4 (count users)))
                (is (= (get users "to-be-modified-1") to-be-modified-1))
                (is (= (get users "to-be-modified-2") to-be-modified-2))
                ))))
        (testing "realm deletion"
          (admin/delete-realm! admin-client realm-name)
          (is (thrown? javax.ws.rs.NotFoundException (admin/get-realm admin-client realm-name))))))))
