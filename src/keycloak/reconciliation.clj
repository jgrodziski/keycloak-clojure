(ns keycloak.reconciliation
  (:require
   [clojure.set :refer [difference]]
   [editscript.core :as e]

   [keycloak.utils :as utils]
   [keycloak.user :as user]
   [keycloak.admin :as admin]
   [keycloak.bean :as bean]
   [keycloak.deployment :as deployment]
   [clojure.pprint :as pp]))

(defn remove-blank-value-entries [m]
  (into {} (filter (fn [[k v]] (not (and (string? v) (clojure.string/blank? v)))) m)))

(defn find-differents
  "find items in current coll that are different from the ones in the desired coll using the optional keys in keyseq to check equality only on these keys"
  ([k current desired]
   (find-differents k current desired nil))
  ([k current desired keyseq]
   (let [current-by-k (utils/associate-by k current)]
     (reduce (fn [acc desired-x]
               (let [k-current (k desired-x)
                     current-x (get current-by-k k-current)]
                 (if (and current-x (or (not= desired-x (remove-blank-value-entries current-x))
                                        (and (seq keyseq) (not= (select-keys desired-x keyseq) (select-keys (remove-blank-value-entries current-x) keyseq)))))
                   (do (println "Caution - not equals!: current:" current-x ", desired:" desired-x)
                       (conj acc desired-x))
                   acc))) (list) desired))))

(defn find-deletions
  "find items in current coll missing from the desired coll"
  [k current desired]
  (let [ids-to-delete (difference (set (map k current)) (set (map k desired)))]
    (filter (fn [x] (ids-to-delete (if (fn? k) (k x) (get x k)))) current)))

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
                                          (let [_      (println (format "Apply %s for step %s" steps-key step))
                                                result (apply-fn step)]
                                            {:result result :success? (not (nil? result)) :error? (nil? result)})
                                          ))])) (keys config))))

(defn users-plan [keycloak-client realm-name desired-users]
  (let [current-users          (->> (user/get-users keycloak-client realm-name)
                                    (map bean/UserRepresentation->map)
                                    (map #(dissoc % :id)));vector of UserRepresentation
        filtered-desired-users (map #(select-keys % [:username :email :first-name :last-name :attributes]) desired-users)]
    (utils/pprint-to-file "/tmp/current-users.edn" current-users)
    {:user/updates   (find-differents :username current-users filtered-desired-users [:username :first-name :last-name :email :attributes])
     :user/deletions (find-deletions  :username current-users desired-users)
     :user/additions (find-additions  :username current-users desired-users)}))

(defn apply-users-plan! [keycloak-client realm-name plan & [opts]]
  (let [apply-deletions? (or (:apply-deletions? opts) false)
        config {:user/additions {:apply-fn    (fn [x]
                                                (let [user (user/create-user! keycloak-client realm-name x)]
                                                  (println (format "User \"%s\" added" (:username x)))
                                                  (bean/UserRepresentation->map user)))
                                 :rollback-fn (fn [x] (user/delete-user! keycloak-client realm-name (user/user-id keycloak-client realm-name (:username x))))}
                :user/updates   {:apply-fn    (fn [x]
                                                (let [user (user/update-user! keycloak-client realm-name (user/user-id keycloak-client realm-name (:username x)) x)]
                                                  (println (format "User \"%s\" updated" (:username x)))
                                                  (bean/UserRepresentation->map user)))
                                 :rollback-fn (fn [x] nil)}
                :user/deletions {:apply-fn    (fn [x]
                                                (when apply-deletions?
                                                  (let [user-id (user/delete-user! keycloak-client realm-name (:username x))]
                                                    (println (format "User with id \"%s\" and username \"%s\" deleted" user-id (:username x)))
                                                    user-id)))
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
        desired-realm-role-mappings  (merge (into {} (map (fn [role] [role nil]) roles));to take into account avery roles in the realm, even the ones not in the desired states for proper deletions
                                            (utils/aggregate-keys-by-values (into {} (map (fn [[user role-mappings]] [user (:realm-roles role-mappings)]) desired-role-mappings))))
        ;;TODO Handle client roles
        ;;current-client-role-mappings (map (fn [[role user-reps]]
        ;;desired-client-role-mappings (utils/aggregate-keys-by-values (map (fn [[user role-mappings]] [user (:client-roles role-mappings)]) desired-role-mappings))
        ;_ (println "current realm-role-mappings")
        ;_ (pp/pprint current-realm-role-mappings)
        ;_ (println "desired realm-role-mappings")
        ;_ (pp/pprint desired-realm-role-mappings)
        ;_ (pp/pprint current-realm-role-mappings)
        additions (->> desired-realm-role-mappings
                       (map (fn [[role desired-users]]
                                  (let [current-users-for-role   (get current-realm-role-mappings role)
                                        existing-users-additions (find-additions identity current-users-for-role desired-users)]
                                    (when role
                                      [role existing-users-additions]))))
                       (into {})
                       (utils/aggregate-keys-by-values)
                       (map (fn [[username realm-roles]] {:username username :realm-roles realm-roles}))
                       vec)
        deletions (->> desired-realm-role-mappings
                       (map (fn [[role users]]
                              (when role
                                [role (find-deletions identity (get current-realm-role-mappings role) users)])))
                       (into {})
                       (utils/aggregate-keys-by-values)
                       (map (fn [[username realm-roles]] {:username username :realm-roles realm-roles}))
                       vec)]
    {:realm-role-mappings/additions additions
     :realm-role-mappings/deletions deletions}))

(defn apply-role-mappings-plan! [keycloak-client realm-name plan & [opts]]
  (let [apply-deletions? (or (:apply-deletions? opts) false)
        config {:realm-role-mappings/additions {:apply-fn    (fn [{:keys [username realm-roles]}]
                                                               (let [roles-added (user/add-realm-roles! keycloak-client realm-name username realm-roles)]
                                                                 (println (format "Roles %s added to user \"%s\"" realm-roles username))
                                                                 {username roles-added}))
                                                :rollback-fn (fn [{:keys [username realm-roles]}]
                                                               (user/remove-realm-roles! keycloak-client realm-name username realm-roles))}
                :realm-role-mappings/deletions {:apply-fn    (fn [{:keys [username realm-roles]}]
                                                               (when apply-deletions?
                                                                 (let [roles-deleted (user/remove-realm-roles! keycloak-client realm-name username realm-roles)]
                                                                   (println (format "Roles \"%s\" deleted from user \"%s\"" realm-roles username))
                                                                   {username roles-deleted})))
                                                :rollback-fn (fn [x] nil)}}]
    (apply-plan keycloak-client realm-name config plan)))

(defn groups-plan [keycloak-client realm-name desired-groups]
  (let [current-groups (map bean/GroupRepresentation->map (admin/list-groups keycloak-client realm-name))]
    {:groups/additions    (find-additions :name current-groups desired-groups)
     :groups/deletions    (find-deletions :name current-groups desired-groups)
     :subgroups/additions (mapcat (fn [{:keys [name subgroups] :as desired-group}]
                                    (let [group-id          (admin/get-group-id keycloak-client realm-name name)
                                          current-subgroups (map bean/GroupRepresentation->map (admin/list-subgroups keycloak-client realm-name group-id))]
                                      (when group-id
                                        (map (fn [subgroup]
                                               (-> subgroup
                                                   (assoc :parent-group-id group-id)
                                                   (assoc :parent-group-name name))) (find-additions :name current-subgroups subgroups))))) desired-groups)
     :subgroups/deletions (mapcat (fn [{:keys [name subgroups] :as desired-group}]
                                    (let [group-id          (admin/get-group-id keycloak-client realm-name name)
                                          current-subgroups (map bean/GroupRepresentation->map (admin/list-subgroups keycloak-client realm-name group-id))]
                                      (when group-id
                                        (map (fn [subgroup]
                                               (-> subgroup
                                                   (assoc :id (admin/get-subgroup-id keycloak-client realm-name group-id (:name subgroup)))
                                                   (assoc :parent-group-id group-id)
                                                   (assoc :parent-group-name name)))
                                             (find-deletions :name current-subgroups subgroups))))) desired-groups)}))

(defn apply-groups-plan! [keycloak-client realm-name plan & [opts]]
  (let [apply-deletions? (or (:apply-deletions? opts) false)
        config           {:groups/additions    {:apply-fn    (fn apply-group-addition-step [{:keys [name subgroups] :as group}]
                                                               (let [created-group (bean/GroupRepresentation->map (admin/create-group! keycloak-client realm-name name))]
                                                                 (println (format "Group \"%s\" created" name))
                                                                 (doseq [subgroup subgroups]
                                                                   (admin/create-subgroup! keycloak-client realm-name (:id created-group) (:name subgroup)))
                                                                 created-group))
                                                :rollback-fn (fn rollback-group-addition-step [group]
                                                               (let [deleted-group (admin/delete-group! keycloak-client realm-name (admin/get-group-id keycloak-client realm-name (:name group)))]
                                                                 (println (format "Group \"%s\" deleted" (:name group)))
                                                                 deleted-group))}
                          :subgroups/additions {:apply-fn    (fn apply-subgroup-addition-step [{:keys [name parent-group-id parent-group-name] :as subgroup}]
                                                               (let [created-group (bean/GroupRepresentation->map (admin/create-subgroup! keycloak-client realm-name parent-group-id name))]
                                                              (println (format "Subgroup \"%s\" of group \"%s\" created" name parent-group-name))))
                                                :rollback-fn (fn rollback-subgroup-addition-step [subgroup]
                                                               (admin/delete-group! keycloak-client realm-name (admin/get-group-id keycloak-client realm-name (:name subgroup))))}
                          :groups/deletions    {:apply-fn    (fn apply-group-deletion-step [group]
                                                               (let [deleted-group (admin/delete-group! keycloak-client realm-name (:id group))]
                                                                 (println (format "Group \"%s\" with id \"%s\" deleted" (:name group) (:id group)))
                                                                 deleted-group))
                                                :rollback-fn (fn rollback-group-deletion-step [group]
                                                               (when apply-deletions?
                                                                 (admin/create-group! keycloak-client realm-name (:name group))))}
                          :subgroups/deletions {:apply-fn (fn apply-subgroup-deletion-step [subgroup]
                                                            (when apply-deletions?
                                                              (let [deleted-subgroup (admin/delete-group! keycloak-client realm-name (:id subgroup))]
                                                                (println (format "Subgroup \"%s\" of group \"%s\" deleted" (:name subgroup) (:parent-group-name subgroup)))
                                                                deleted-subgroup)))}}]
    (apply-plan keycloak-client realm-name config plan)))

(defn reconciliate-users! [^org.keycloak.admin.client.Keycloak admin-client realm-name users & [opts]]
  (println (format "Will reconciliate users of realm %s, dry-run? %s" realm-name (:dry-run? opts)))
  (let [dry-run?     (or (:dry-run? opts) false)
        plan         (users-plan admin-client realm-name users)
        _            (do (println "Users reconciliation plan is:") (clojure.pprint/pprint plan))
        report       (when (not dry-run?)
                       (println "Will apply the previous Users reconciliation plan! apply-deletions?" (:apply-deletions? opts))
                       (apply-users-plan! admin-client realm-name plan opts))]
    (when report
      (clojure.pprint/pprint report))))

(defn reconciliate-role-mappings! [^org.keycloak.admin.client.Keycloak admin-client realm-name roles users & [opts]]
  (println (format "will reconciliate users roles mappings of realm %s, dry-run? %s" realm-name (:dry-run? opts)))
  (let [dry-run?     (or (:dry-run? opts) false)
        users->roles (utils/associate-by :username users)
        plan         (role-mappings-plan admin-client realm-name roles users->roles)
        _            (do (println "User Role Mappings reconciliation plan is:") (clojure.pprint/pprint plan))
        report       (when (not dry-run?)
                       (println "Will apply the previous User-Role Mappings reconciliation plan! apply-deletions?" (:apply-deletions? opts) )
                       (apply-role-mappings-plan! admin-client realm-name plan opts))]
    (when report
      (clojure.pprint/pprint report))))

(defn reconciliate-groups! [^org.keycloak.admin.client.Keycloak admin-client realm-name groups & [opts]]
  (println (format "Will reconciliate Groups and Subgroups of realm %s, dry-run? %s" realm-name (:dry-run? opts)))
  (let [dry-run? (or (:dry-run? opts) false)
        plan     (groups-plan admin-client realm-name groups)
        _        (do (println "Groups reconciliation plan is:") (clojure.pprint/pprint plan))
        report   (when (not dry-run?)
                   (println "Will apply the previous Groups reconciliation plan! apply-deletions?" (:apply-deletions? opts))
                   (apply-groups-plan! admin-client realm-name plan opts))]
    (when report
      (clojure.pprint/pprint report))))

(defn plan [^org.keycloak.admin.client.Keycloak admin-client realm-name desired-state & [opts]]
  (let [users-plan         (users-plan admin-client realm-name (:users desired-state))
        groups-plan        (groups-plan admin-client realm-name (:groups desired-state))
        role-mappings-plan (role-mappings-plan admin-client realm-name (:roles desired-state) (utils/associate-by :username (:users desired-state)))]
    (merge users-plan groups-plan role-mappings-plan)))

(defn apply-plan! [^org.keycloak.admin.client.Keycloak admin-client realm-name plan & [opts]]
  (let [users-report (apply-users-plan! admin-client realm-name plan opts)
        role-mappings-report (apply-role-mappings-plan! admin-client realm-name plan opts)
        groups-report (apply-groups-plan! admin-client realm-name plan opts)]
    (merge users-report role-mappings-report groups-report)))

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
