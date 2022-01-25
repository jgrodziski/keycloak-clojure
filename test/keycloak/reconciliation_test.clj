(ns keycloak.reconciliation-test
  (:require
   [clojure.test :as t :refer :all]
   [clojure.tools.logging :as log :refer [info]]
   [clojure.pprint :as pp]
   [testit.core :refer :all]

   [keycloak.admin :as admin]
   [keycloak.user :as user]
   [keycloak.deployment :as deployment]
   [keycloak.reconciliation :refer :all]
   [keycloak.utils-test :refer [minikube-keycloak-service-or-localhost]]
   [keycloak.utils :as utils :refer [letdef]]
   [keycloak.bean :as bean]))

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
             (user/create-user! keycloak-client realm-name user)
             (user/set-realm-roles! keycloak-client realm-name (:username user) roles)
             user))))

(defn generate-and-create-users-with-usernames [keycloak-client realm-name roles usernames]
  (doall (for [username usernames]
           (let [user (user/generate-user username)]
             (user/create-user! keycloak-client realm-name user)
             (user/set-realm-roles! keycloak-client realm-name username roles)
             user))))

(deftest ^:integration users-plan-test
  (let [admin-client (deployment/keycloak-client integration-test-conf admin-login admin-password)]
    (testing "realm, roles and generated users creation for"
      (let [realm-name      (str "test-realm-" (rand-int 1000))
            realm           (admin/create-realm! admin-client {:name realm-name})
            roles           #{"role1" "role2" "role3" "role4"}
            _               (admin/create-roles! admin-client realm-name roles)
            _               (do  (log/info "realm created"))
            generated-users-1 (generate-and-create-users admin-client realm-name roles 2)
            generated-users-2 (concat generated-users-1 (generate-and-create-users-with-usernames admin-client realm-name roles #{"to-be-modified-1" "to-be-modified-2"}))
            to-be-modified2   (second generated-users-2)
            generated-users-3 (concat generated-users-2 (generate-and-create-users-with-usernames admin-client realm-name roles #{"to-be-deleted-1"  "to-be-deleted-2"}))]
        (is (= realm-name (.getRealm realm)))
        ;(Thread/sleep 8000)
        ;(prn (user/get-users admin-client realm-name))
        (testing "make plan with additions"
          (let [user-in-addition (user/generate-user "to-be-added-1")
                plan (users-plan admin-client realm-name [user-in-addition])]
            (is (= 1 (count (:user/additions plan))))
            (is (= "to-be-added-1" (:username (first (:user/additions plan)))))))
        (testing "make plan with deletion"
          (let [plan (users-plan admin-client realm-name generated-users-2)]
            (pp/pprint plan)
            (is (= 2 (count (:user/deletions plan))))
            (is (= "to-be-deleted-1" (:username (first (:user/deletions plan)))))
            (is (= "to-be-deleted-2" (:username (second (:user/deletions plan)))))))
        (testing "make plan with updates"
          (let [plan (users-plan admin-client realm-name [(user/generate-user "to-be-modified-1") to-be-modified2])]
            (is (= 1 (count (:user/updates plan))))
            (is (= "to-be-modified-1" (:username (first (:user/updates plan)))))))
        (testing "realm deletion"
          (admin/delete-realm! admin-client realm-name)
          (is (thrown? javax.ws.rs.NotFoundException (admin/get-realm admin-client realm-name)))))))
  (testing "Fetch the existing users and diff the one that need update or creation"
    (let [])))


(deftest ^:integration users-plan-apply-test
  (let [admin-client (deployment/keycloak-client integration-test-conf admin-login admin-password)]
    (testing "realm, roles and generated users creation for"
      (let [realm-name      (str "test-realm-" (rand-int 1000))
            realm           (admin/create-realm! admin-client {:name realm-name})
            roles           #{"role1" "role2" "role3" "role4"}
            _               (admin/create-roles! admin-client realm-name roles)
            _               (do  (log/info "realm created"))
            generated-users-1 (generate-and-create-users admin-client realm-name roles 2)
            generated-users-2 (concat generated-users-1 (generate-and-create-users-with-usernames admin-client realm-name roles #{"to-be-modified-1" "to-be-modified-2"}))
            generated-users-3 (concat generated-users-2 (generate-and-create-users-with-usernames admin-client realm-name roles #{"to-be-deleted-1"  "to-be-deleted-2"}))]
        (is (= realm-name (.getRealm realm)))
        (testing "make plan with additions"
          (let [user-in-addition (user/generate-user "to-be-added-1")
                desired-state    (conj generated-users-3 user-in-addition)
                plan             (users-plan admin-client realm-name desired-state)]
            (is (= 1 (count (:user/additions plan))))
            (is (= "to-be-added-1" (:username (first (:user/additions plan)))))
            (testing "Apply plan with additions"
              (let [report (apply-users-plan! admin-client realm-name plan)
                    users  (utils/associate-by :username (user/get-users-beans admin-client realm-name))]
                (is (= 7 (count users)))
                (is (get users "to-be-added-1") user-in-addition)))
            (testing "Plan application should makes an empty plan afterwards"
              (let [empty-plan (users-plan admin-client realm-name (conj generated-users-3 user-in-addition))]
                (facts
                 (get empty-plan :users/additions) => empty?
                 (get empty-plan :users/updates)   => empty?
                 (get empty-plan :users/deletions) => empty?)))))
        (testing "make plan with deletions"
          (let [plan (users-plan admin-client realm-name generated-users-2)]
            (is (= 3 (count (:user/deletions plan))))
            (testing "Apply plan with deletions"
              (let [report (apply-users-plan! admin-client realm-name plan {:apply-deletions? true})
                    users  (utils/associate-by :username (user/get-users-beans admin-client realm-name))]
                (is (= 4 (count users)))
                (is (nil? (get users "to-be-deleted-1")))
                (is (nil? (get users "to-be-deleted-2")))
                (is (nil? (get users "to-be-added-1"))))
              (testing "Plan application should makes an empty plan afterwards"
                (let [empty-plan (users-plan admin-client realm-name generated-users-2)]
                  (facts
                   (get empty-plan :users/additions) => empty?
                   (get empty-plan :users/updates)   => empty?
                   (get empty-plan :users/deletions) => empty?))))))
        (testing "make plan with updates"
          (let [to-be-modified-1 (user/generate-user "to-be-modified-1")
                to-be-modified-2 (user/generate-user "to-be-modified-2")
                plan (users-plan admin-client realm-name (conj generated-users-1 to-be-modified-1 to-be-modified-2))]
            (is (= 2 (count (:user/updates plan))))
            (is (= "to-be-modified-1" (:username (first (:user/updates plan)))))
            (testing "Apply plan with updates"
              (let [report (apply-users-plan! admin-client realm-name plan)
                    users  (utils/associate-by :username (conj generated-users-1 to-be-modified-1 to-be-modified-2))]
                (is (= 4 (count users)))
                (facts
                 (get users "to-be-modified-1") =in=> (select-keys to-be-modified-1 [:username :first-name :last-name :email])
                 (get users "to-be-modified-2") =in=> (select-keys to-be-modified-2 [:username :first-name :last-name :email]))))
            (testing "Plan application should makes an empty plan afterwards"
                (let [empty-plan (users-plan admin-client realm-name generated-users-2)]
                  (facts
                   (get empty-plan :users/additions) => empty?
                   (get empty-plan :users/updates)   => empty?
                   (get empty-plan :users/deletions) => empty?)))))
        (testing "realm deletion"
          (admin/delete-realm! admin-client realm-name)
          (is (thrown? javax.ws.rs.NotFoundException (admin/get-realm admin-client realm-name))))))))

(defn- get-roles->users [admin-client realm-name roles]
  (into {} (map (fn [[role users]] [role (into [] (map bean/UserRepresentation->map users))]) (user/get-users-aggregated-by-realm-roles admin-client realm-name roles))))

(deftest ^:integration user-roles-test
  (let [admin-client (deployment/keycloak-client integration-test-conf admin-login admin-password)]
    (testing "realm, roles and generated users creation for"
      (let [realm-name      (str "test-realm-" (rand-int 1000))
            realm           (admin/create-realm! admin-client {:name realm-name})
            roles           #{"role1" "role2" "role3" "role4"}
            _               (admin/create-roles! admin-client realm-name roles)
            _               (do (log/info "realm created"))
            generated-users (generate-and-create-users-with-usernames admin-client realm-name #{"role1" "role2"} #{"user1" "user2"})
            roles->users    (get-roles->users admin-client realm-name roles)]
        (is (= realm-name (.getRealm realm)))
        (is (= 2 (count (get-in roles->users ["role1"]))))
        (is (= 2 (count (get-in roles->users ["role2"]))))
        (fact (map bean/RoleRepresentation->map (admin/list-roles admin-client realm-name)) =in=> ^:in-any-order [{:name "role1"} {:name "role2"} {:name "role3"} {:name "role4"}])
        (testing "roles addition to users with a plan"
          (let [desired-state {"user1" {:realm-roles ["role1" "role2" "role3"]} "user2" {:realm-roles ["role1" "role2"]} "user3" {:realm-roles ["role1" "role2" "role4"]}}
                plan (role-mappings-plan admin-client realm-name roles desired-state)]
            (is (= [{:username "user3" :realm-roles ["role1" "role4" "role2"]}
                    {:username "user1" :realm-roles ["role3"]}] (get plan :realm-role-mappings/additions)))
            (testing "then plan is applied with role additions"
              (let [;;user must be created in the realm before trying to apply a role mapping to it
                    user4        (generate-and-create-users-with-usernames admin-client realm-name #{"role1" "role2" "role4"} #{"user3"})
                    report       (apply-role-mappings-plan! admin-client realm-name plan)
                    roles->users (get-roles->users admin-client realm-name roles)]
                (is (= 3 (count (get-in roles->users ["role1"]))))
                (is (= 3 (count (get-in roles->users ["role2"]))))
                (is (= 1 (count (get-in roles->users ["role3"]))))
                (is (= "user3"  (get-in roles->users ["role4" 0 :username])))
                (is (= "user1" (get-in roles->users ["role3" 0 :username])))))
            (testing "Plan application should makes an empty plan afterwards"
                (let [empty-plan (role-mappings-plan admin-client realm-name roles desired-state)]
                  (facts
                   (get empty-plan :realm-role-mappings/additions) => empty?
                   (get empty-plan :realm-role-mappings/deletions) => empty?)))))
        (testing "roles deletions to users with a plan"
          (let [desired-state {"user1" {:realm-roles ["role1"]}
                               "user2" {:realm-roles ["role1" "role2"]}}
                plan (role-mappings-plan admin-client realm-name roles desired-state)]
            (is (= [{:username "user3" :realm-roles ["role1" "role4" "role2"]}
                    {:username "user1" :realm-roles ["role3" "role2"]}] (get plan :realm-role-mappings/deletions)))
            (testing "then plan is applied with role deletions"
              (let [report       (apply-role-mappings-plan! admin-client realm-name plan {:apply-deletions? true})
                    roles->users (get-roles->users admin-client realm-name roles)]
                (is (= 1 (count (get-in roles->users ["role2"]))))
                (is (= 2 (count (get-in roles->users ["role1"]))))))
            (testing "Plan application should makes an empty plan afterwards"
                (let [empty-plan (role-mappings-plan admin-client realm-name roles desired-state)]
                  (facts
                   (get empty-plan :realm-role-mappings/additions) => empty?
                   (get empty-plan :realm-role-mappings/deletions) => empty?)))))
        (testing " finally realm is deleted"
          (admin/delete-realm! admin-client realm-name)
          (is (thrown? javax.ws.rs.NotFoundException (admin/get-realm admin-client realm-name))))))))

(deftest ^:integration groups-test
  (let [admin-client (deployment/keycloak-client integration-test-conf admin-login admin-password)]
    (testing "realm, roles and groups creation for"
      (let [realm-name     (str "test-realm-" (rand-int 1000))
            realm          (admin/create-realm! admin-client {:name realm-name})
            _              (do (log/info "realm created"))
            group-names    #{"group1" "group2" "group3" "group4"}
            groups         (admin/create-groups! admin-client realm-name group-names)
            subgroup1a     (admin/create-subgroup! admin-client realm-name (admin/get-group-id admin-client realm-name "group1") "subgroup1a")
            subgroup2a     (admin/create-subgroup! admin-client realm-name (admin/get-group-id admin-client realm-name "group2") "subgroup2a")
            created-groups (map bean/GroupRepresentation->map (admin/list-groups admin-client realm-name))]
        (facts (admin/count-groups admin-client realm-name) => 6
               (count created-groups) => 4
               (-> created-groups first :subGroups count) => 1)
        (testing "add new group and subgroups"
          (let [desired-state [{:name "group1" :subgroups [{:name "subgroup1a"} {:name "subgroup1b"}]};new subgroup to existing group with already existing subgroup and a remvoed one "subgroup1b"
                                                           {:name "group2" :subgroups [{:name "subgroup2a"}]};existing group and subgroup should not move
                                                           {:name "group3" :subgroups [{:name "subgroup3a"}]};new subgroup to existing group with no subgroups
                                                           {:name "group4"}
                                                           {:name "group5"}                                  ;new group
                                                           {:name "group6" :subgroups [{:name "subgroup6a"}]};new group with new subgroup
                                                          ]
                plan (groups-plan admin-client realm-name desired-state)]
            (facts plan                                      =in=> {:groups/additions [{:name "group5"}
                                                                                       {:name "group6" :subgroups [{:name "subgroup6a"}]}]}
                   (:subgroups/additions plan)               =in=> [{:name "subgroup1b"}
                                                                    {:name "subgroup3a"}]
                   (-> plan :subgroups/additions first keys) => (list :name :parent-group-id :parent-group-name))
            (testing "then plan is applied with group additions"
              (let [report (apply-groups-plan! admin-client realm-name plan)
                    groups (map bean/GroupRepresentation->map (admin/list-groups admin-client realm-name))]
                (facts (count groups) => 6
                       (-> groups first :subGroups count) => 2
                       (-> groups first :subGroups)       =in=> ^:in-any-order [{:name "subgroup1b"} {:name "subgroup1a"}]
                       (-> groups (nth 2) :subGroups)     =in=> [{:name "subgroup3a"}])))
            (testing "Plan application should makes an empty plan afterwards"
                (let [empty-plan (groups-plan admin-client realm-name desired-state)]
                  (facts
                   (get empty-plan :groups/additions) => empty?
                   (get empty-plan :groups/deletions) => empty?)))))
        (testing "group deletions"
          (let [desired-state [{:name "group1" :subgroups [{:name "subgroup1b"}] };subgroup1a shoud be deleted
                                                           {:name "group2"};;subgroup should be deleted
                                                           {:name "group3"}
                                                           ;;group 4 should be deleted
                                                           {:name "group5"}
                                                           {:name "group6" :subgroups [{:name "subgroup6a"}]}]
                plan (groups-plan admin-client realm-name desired-state)]
            (facts (-> plan :groups/deletions)          =in=>                [{:name "group4"}]
                   (-> plan :subgroups/additions count) => 0
                   (-> plan :subgroups/deletions)       =in=> ^:in-any-order [{:name "subgroup1a"} {:name "subgroup2a"}])
            (testing "applying group deletion plan"
              (let [report (apply-groups-plan! admin-client realm-name plan {:apply-deletions? true})
                    groups (map bean/GroupRepresentation->map (admin/list-groups admin-client realm-name))]
                (facts (count groups) => 5
                       groups =in=> ^:in-any-order [{:name "group1"} {:name "group2"} {:name "group3"}{:name "group5"}{:name "group6"}]
                       (-> groups first :subGroups) =in=> [{:name "subgroup1b"}]
                       (-> groups last :subGroups) =in=> [{:name "subgroup6a"}])))
            (testing "Plan application should makes an empty plan afterwards"
                (let [empty-plan (groups-plan admin-client realm-name desired-state)]
                  (facts
                   (get empty-plan :groups/additions) => empty?
                   (get empty-plan :groups/deletions) => empty?)))))
        (testing " finally realm is deleted"
          (admin/delete-realm! admin-client realm-name)
          (is (thrown? javax.ws.rs.NotFoundException (admin/get-realm admin-client realm-name))))))))


(comment
  (do
    (def transverse-hors-prod-conf (deployment/client-conf transverse-url "master" "admin-cli"))
    (def transverse-admin-client (deployment/keycloak-client transverse-hors-prod-conf transverse-login transverse-pwd))
    (def current-users (clojure.edn/read-string (slurp "/tmp/current-users.edn")))
    (def data (clojure.edn/read-string (slurp "resources/config-init-realm.edn")))

    (def admin-client (deployment/keycloak-client integration-test-conf admin-login admin-password))
    (def realm-name "perm")
    (def realm           (admin/create-realm! admin-client {:name realm-name}))
    (def roles           #{"role1" "role2" "role3" "role4"})
    (admin/create-roles! admin-client realm-name roles)
    (generate-and-create-users-with-usernames admin-client realm-name #{"role1" "role2"} #{"user1"})
    )

  (let [admin-client (deployment/keycloak-client integration-test-conf admin-login admin-password)]
    (user/create-or-update-user! admin-client "test-realm-214" {:username "fake-user-311" :first-name "Ashley" :last-name "Fernandez" :email "fake-user-311@me.com" :attributes {"test" ["yo"]} :password "password"} nil nil))

  )
