(ns keycloak.reconciliation
  (:require
   [clojure.set :refer [difference]]
   [editscript.core :as e]
   [keycloak.utils :as utils]
   [keycloak.user :as user]
   [keycloak.admin :as admin]
   [keycloak.bean :as bean]
   [keycloak.deployment :as deployment]))

(defn find-differents
  "find items in current coll that are different from the ones in the desired coll"
  [k current desired]
  ;(prn "find differents " current desired)
  (let [current-by-k (utils/associate-by k current)]
    (reduce (fn [acc desired-x]
              (let [k-current (k desired-x)
                    current-x (get current-by-k k-current)]
                (if (and current-x (not= desired-x current-x))
                  (do (prn "not equals " current-x desired-x)
                      (conj acc desired-x))
                  acc))) (list) desired)))

(defn find-deletions
  "find items in current coll missing from the desired coll"
  [k current desired]
  (let [ids-to-delete (difference (set (map k current)) (set (map k desired)))]
    (filter #(ids-to-delete (k %)) current)))

(defn find-additions
  "find items in desired coll missing from the current coll"
  [k current desired]
  (let [missing-ids (difference (set (map k desired)) (set (map k current)))]
    (filter #(missing-ids (k %)) desired)))

(defn apply-plan [keycloak-client realm-name config plan]
  (into {} (map (fn [steps-key]
                    (let [apply-fn (get-in config [steps-key :apply-fn])
                          steps    (get plan steps-key)]
                      [steps-key (doall (for [step steps]
                                          (let [_      (println (format "Apply function for key %s and step %s" steps-key step))
                                                result (apply-fn step)]
                                            {:result result :success? (not (nil? result)) :error? (nil? result)})
                                          ))])) (keys config))))

(defn users-plan [keycloak-client realm-name desired-users]
  (let [current-users          (->> (user/get-users keycloak-client realm-name)
                                    (map bean/UserRepresentation->map)
                                    (map #(dissoc % :id)));vector of UserRepresentation
        filtered-desired-users (map #(select-keys % [:username :email :first-name :last-name :attributes]) desired-users)]
    {:user/updates   (find-differents :username current-users filtered-desired-users)
     :user/deletions (find-deletions  :username current-users desired-users)
     :user/additions (find-additions  :username current-users desired-users)}))

(defn apply-users-plan [keycloak-client realm-name plan]
  (let [config {:user/additions {:apply-fn    (fn [x]
                                                (let [user (user/create-user! keycloak-client realm-name x)]
                                                  (println (format "User %s added" (:username x)))
                                                  (bean/UserRepresentation->map user)))
                                 :rollback-fn (fn [x] (user/delete-user! keycloak-client realm-name (user/user-id keycloak-client realm-name (:username x))))}
                :user/updates   {:apply-fn    (fn [x]
                                                (let [user (user/update-user! keycloak-client realm-name (user/user-id keycloak-client realm-name (:username x)) x)]
                                                  (println (format "User %s updated" (:username x)))
                                                  (bean/UserRepresentation->map user)))
                                 :rollback-fn (fn [x] nil)}
                :user/deletions {:apply-fn    (fn [x]
                                                (let [user-id (user/delete-user! keycloak-client realm-name (:username x))]
                                                  (println (format "User with id %s deleted" user-id))
                                                  user-id))
                                 :rollback-fn (fn [x] nil)}}]
    (apply-plan keycloak-client realm-name config plan)))

(defn role-mappings-plan
  "Make a role plan, all considered roles must be given as input and the desired role-mappings as {\"username\" {:realm-roles [\"role1\"]
                                                                                                                 :client-roles {:client-id1  [\"role2\"]}}}
  return a map with keys as :realm-role-mapping/additions and deletions with a map of user to roles
  NB: only realm roles for the moment"
  [keycloak-client realm-name roles desired-role-mappings]
  (let [current-realm-role-mappings  (into {} (map (fn [[role user-reps]]
                                                     [role (map #(.getUsername %) user-reps)]) (user/get-users-aggregated-by-realm-roles keycloak-client realm-name roles)))
        desired-realm-role-mappings  (utils/aggregate-keys-by-values (into {} (map (fn [[user role-mappings]] [user (:realm-roles role-mappings)]) desired-role-mappings)))
        ;;TODO Handle client roles
        ;;current-client-role-mappings (map (fn [[role user-reps]]
        ;;desired-client-role-mappings (utils/aggregate-keys-by-values (map (fn [[user role-mappings]] [user (:client-roles role-mappings)]) desired-role-mappings))
        additions (into {} (map (fn [[role users]]
                                  [role (find-additions identity (get current-realm-role-mappings role) users)]) desired-realm-role-mappings))
        deletions (into {} (map (fn [[role users]]
                                  [role (find-deletions identity (get current-realm-role-mappings role) users)]) desired-realm-role-mappings))]
    {:realm-role-mappings/additions (utils/aggregate-keys-by-values additions)
     :realm-role-mappings/deletions (utils/aggregate-keys-by-values deletions)}))

(defn apply-roles-plan [keycloak-client realm-name plan]
  (let [config {:realm-role-mappings/additions {:apply-fn    (fn [[username roles-to-add]]
                                                               (let [roles-added (user/add-realm-roles! keycloak-client realm-name username roles-to-add)]
                                                                 (println (format "Roles %s added to user %s" roles-to-add username))
                                                                 {username roles-added}))
                                                :rollback-fn (fn [[username roles-to-add]]
                                                               (user/remove-realm-roles! keycloak-client realm-name username roles-to-add))}
                :realm-role-mappings/deletions {:apply-fn    (fn [[username roles-to-delete]]
                                                               (let [roles-deleted (user/remove-realm-roles! keycloak-client realm-name username roles-to-delete)]
                                                                 (println (format "Roles %s deleted from user %s" roles-to-delete username))
                                                                 {username roles-deleted}))
                                                :rollback-fn (fn [x] nil)}}]
    (apply-plan keycloak-client realm-name config plan)))

(defn groups-plan [keycloak-client realm-name desired-groups]
  (let [current-groups (map bean/GroupRepresentation->map (admin/list-groups keycloak-client realm-name))]
    {:groups/additions    (find-additions :name current-groups desired-groups)
     ;;TODO memoize the subgroups listing
     #_:subgroups/additions #_(mapcat (fn [{:keys [name subgroups] :as desired-group}]
                                    (let [group-id          (admin/get-group-id keycloak-client realm-name name)
                                          _ (prn "group-id" group-id name)
                                          current-subgroups (map bean/GroupRepresentation->map (admin/list-subgroups keycloak-client realm-name name))]
                                        (find-additions :name current-subgroups subgroups))) desired-groups)
     :groups/deletions    (find-deletions :name current-groups desired-groups)
     #_:subgroups/deletions #_(mapcat (fn [{:keys [name subgroups] :as desired-group}]
                                    (let [current-subgroups (map bean/GroupRepresentation->map (admin/list-subgroups keycloak-client realm-name name))]
                                        (find-deletions :name current-subgroups subgroups))) desired-groups)}))

(defn apply-groups-plan [keycloak-client realm-name plan]
  (let [config {:groups/additions {:apply-fn    (fn [group]
                                                  (admin/create-group! keycloak-client realm-name (:name group)))
                                   :rollback-fn (fn [group]
                                                  (admin/delete-group! keycloak-client realm-name
                                                                       (admin/get-group-id keycloak-client realm-name (:name group))))}
                :groups/deletions {:apply-fn (fn [group]
                                               (admin/delete-group! keycloak-client realm-name
                                                                    (admin/get-group-id keycloak-client realm-name (:name group))))
                                   :rollback-fn (fn [group]
                                                  (admin/create-group! keycloak-client realm-name (:name group)))}}]
    (apply-plan keycloak-client realm-name config plan)))


(comment
  (def admin-login "admin")
  (def admin-password "secretadmin")
  (def auth-server-url (keycloak.utils-test/minikube-keycloak-service-or-localhost))
  (def integration-test-conf (deployment/client-conf auth-server-url "master" "admin-cli"))
  (def keycloak-client (deployment/keycloak-client integration-test-conf admin-login admin-password))

  (def realm-name (str "test-realm-" (rand-int 1000)) )
  (def realm      (admin/create-realm! admin-client {:name realm-name}))
  (def roles #{"role1" "role2" "role3" "role4"})
  (admin/create-roles! admin-client realm-name roles)

  (def generated-users (atom {}))
  (for [i (range  8)]
    (let [user (user/generate-user)]
      (swap! generated-users conj [(:username user) user])
      (user/create-user! keycloak-client realm-name user)
      (user/set-realm-roles! keycloak-client realm-name (:username user) roles)))
  (user/create-user! keycloak-client realm-name (user/generate-user "user-that-will-be-modified1"))
  (user/set-realm-roles! keycloak-client realm-name "user-that-will-be-modified1" roles)
  (user/create-user! keycloak-client realm-name (user/generate-user "user-that-will-be-modified2"))
  (user/set-realm-roles! keycloak-client realm-name "user-that-will-be-modified2" roles)
  (user/create-user! keycloak-client realm-name (assoc (user/generate-user "user-that-will-be-modified3") :attributes {"org-ref" ["yo"]}))
  (user/create-user! keycloak-client realm-name (user/generate-user "user-that-will-be-deleted"))
  ;(def users (fetch-current-users-state keycloak-client realm-name))

  (aggregate-by-role {"user1" [:a :b :c] "user2" [:b] "user3" [:a :b :c]})

)
