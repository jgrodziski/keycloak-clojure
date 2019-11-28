(ns keycloak.starter
  (:require [keycloak.admin :refer :all]
            [keycloak.user :as user]
            [keycloak.deployment :as deployment :refer [keycloak-client client-conf]]
            [talltale.core :as talltale :refer :all]
            [me.raynes.fs :as fs]))

(defn export-secret! [keycloak-client realm-name client-id key]
  (let [secret (get-client-secret keycloak-client realm-name client-id)
        home (System/getenv "HOME")
        secrets-file (clojure.java.io/file home ".secrets.edn")
        _ (fs/touch secrets-file)
        secrets (or (clojure.edn/read-string (slurp secrets-file)) {})]
    (println (format "Secret of client \"%s\" exported in file %s at key [:keycloak %s]" client-id (str secrets-file) key))
    (spit secrets-file (assoc-in secrets [:keycloak key] secret))))

(defn create-mappers! [keycloak-client realm-name client-id]
  (println "Create protocol mappers for client" client-id)
  (create-protocol-mapper! keycloak-client realm-name client-id
                           (group-membership-mapper "group-mapper" "group"))
  (create-protocol-mapper! keycloak-client realm-name client-id
                           (user-attribute-mapper "org-ref-mapper" "org-ref" "org-ref" "String")))

(defn generate-user [username-creator-fn role group subgroup idx & opts]
  (merge (talltale/person)
         {:username (apply username-creator-fn role group subgroup idx opts)
          :password "password"}))

(defn init!
  "Create a structure of keycloak objects (realm, clients, roles) and fill it with groups and users"
  [admin-client data]
  (let [realm-name (get-in data [:realm :name])
        {:keys [themes login smtp]} (:realm data)]
    (try (create-realm! admin-client realm-name themes login smtp) (catch Exception e (get-realm admin-client realm-name)))
    (println (format "Realm \"%s\" created" realm-name))

    (doseq [{:keys [name public? redirect-uris web-origins] :as client-data} (:clients data)]
      (let [client (client client-data)]
        (create-client! admin-client realm-name client)
        (println (format "Client \"%s\" created in realm %s" name realm-name)))
      (create-mappers! admin-client realm-name name)
      (export-secret! admin-client realm-name name (keyword (str "secret-" name))))
    (println (format "%s Clients created in realm %s" (count (:clients data)) realm-name))

    (doseq [role (:roles data)]
      (try (create-role! admin-client realm-name role) (catch Exception e (get-role admin-client realm-name role))))

    (doseq [{:keys [name subgroups]} (:groups data)]
      (let [group (create-group! admin-client realm-name name)]
        (println (format "Group \"%s\" created" name))
        (doseq [[idx {subgroup-name :name attributes :attributes}] (map-indexed vector subgroups)]
          (let [subgroup (create-subgroup! admin-client realm-name (.getId group) subgroup-name attributes)]
            (println (format "   Subgroup \"%s\" created in group \"%s\"" subgroup-name name))
            ;;Generated users
            (doseq [role (:roles data)]
              (doseq [i (range 1 (inc (:generated-users-by-group-and-role data)))]
                (let [user (generate-user (:username-creator-fn data) role name subgroup i)
                      created-user (user/create-or-update-user! admin-client realm-name user (:realm-roles data) (:client-roles data))]
                  (println (format "      User \"%s\" created" (:username user)))
                  (println (format "      Add user \"%s\" to group \"%s\"" (:username user) subgroup-name))
                  (add-user-to-group! admin-client realm-name (.getId subgroup) (.getId created-user)))))))))

    ;;Static users
    (doseq [{:keys [username] :as user} (:users data)]
      (let [created-user (user/create-or-update-user! admin-client realm-name user (:realm-roles user) (:client-roles user))]
        (println (format "User \"%s\" created" username))
        (doseq [subgroup-name (:in-subgroups user)]
          (let [subgroup-id (get-subgroup-id admin-client realm-name (get-group-id admin-client realm-name (:group user)) subgroup-name)]
            (println (format "Add user \"%s\" to group \"%s\"" username subgroup-name))
            (add-user-to-group! admin-client realm-name subgroup-id (.getId created-user))))))
    (println (format "Keycloak with realm \"%s\" initialized" realm-name))
    data))
