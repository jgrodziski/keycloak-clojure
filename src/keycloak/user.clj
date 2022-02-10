(ns keycloak.user
  (:refer-clojure :exclude [count])
  (:require [clojure.tools.logging :as log :refer [info warn]]
            [clojure.string :as string :refer [last-index-of]]
            [clojure.java.data :refer [from-java]]
            [clojure.java.io :as io]
            [cheshire.core :as json :refer [encode]]
            [talltale.core :as talltale]
            [keycloak.bean :as bean]
            [keycloak.utils :as utils :refer [hint-typed-doto set-attributes]])
  (:import [org.keycloak.representations.idm CredentialRepresentation UserRepresentation RoleRepresentation]
           [org.keycloak.admin.client.resource ClientResource]
           [org.keycloak.admin.client Keycloak]
           [javax.ws.rs.core Response]))

;(set! *warn-on-reflection* true)

(defn extract-id [^Response resp]
  (when resp
    (when-let [loc (.getLocation resp)]
      (subs (str loc) (+ (last-index-of (str loc) "/") 1)))))

(defn user-for-update
  ^org.keycloak.representations.idm.UserRepresentation
  [{:keys [username first-name last-name email enabled attributes password] :or {enabled true} :as person}]
  (let [user-no-password (if attributes
                           (set-attributes ^org.keycloak.representations.idm.UserRepresentation
                                           (hint-typed-doto "org.keycloak.representations.idm.UserRepresentation" (UserRepresentation.)
                                                            (.setUsername username)
                                                            (.setFirstName first-name)
                                                            (.setLastName last-name)
                                                            (.setEmail email)
                                                            (.setEnabled enabled)
                                                            ;;setRealmRoles has a bug with the admin REST API and doesn't work
                                                            )
                                           attributes)
                           (hint-typed-doto "org.keycloak.representations.idm.UserRepresentation" (UserRepresentation.)
                                            (.setUsername username)
                                            (.setFirstName first-name)
                                            (.setLastName last-name)
                                            (.setEmail email)
                                            (.setEnabled enabled)
                                            ;;setRealmRoles has a bug with the admin REST API and doesn't work
                                            ))]
     (if password
       (doto user-no-password
         (.setCredentials [(hint-typed-doto "org.keycloak.representations.idm.CredentialRepresentation" (CredentialRepresentation.)
                                            (.setType CredentialRepresentation/PASSWORD)
                                            (.setValue password))]))
       user-no-password)))

(defn user-for-enablement ^org.keycloak.representations.idm.UserRepresentation
  [enabled?]
  (hint-typed-doto "org.keycloak.representation.idm.UserRepresentation" (UserRepresentation.)
                   (.setEnabled enabled?)))

(defn user-for-creation
  ([{:keys [username first-name last-name email password attributes] :as person}]
   (when (empty? password) (throw (ex-info "user MUST have a password otherwise the login will throw a NPE" {:person person})))
   (user-for-update person))
  ([{:keys [username first-name last-name email password attributes] :as person} ^java.util.Collection required-actions]
   (doto (user-for-update person)
     (.setRequiredActions (java.util.ArrayList. required-actions)))))

(defn search-user
  ([^org.keycloak.admin.client.Keycloak keycloak-client realm-name user-attribute]
   (try
     (-> keycloak-client (.realm realm-name) (.users) (.search user-attribute (int 0) (int 10)))
     (catch javax.ws.rs.NotFoundException nfe nil)))
  ([^org.keycloak.admin.client.Keycloak keycloak-client realm-name username first-name last-name email]
   (try
     (-> keycloak-client (.realm realm-name) (.users) (.search username first-name last-name email (int 0) (int 10)))
     (catch javax.ws.rs.NotFoundException nfe nil))))

(defn- exact-match
  ([users attr]
   (some (fn [user]
           (when (and attr (or (= (.getUsername user)  attr)
                               (= (.getFirstName user) attr)
                               (= (.getLastName user)  attr)
                               (= (.getEmail user)     attr))) user)) users))
  ([users username-in first-name-in last-name-in email-in]
   (some (fn [user]
           (when (and (= (.getUsername user)  username-in)
                      (= (.getFirstName user) first-name-in)
                      (= (.getLastName user)  last-name-in)
                      (= (.getEmail user)     email-in)) user)) users)))

(defn user-id
  "Return a user-id from either one of (username|first-name|last-name|email) attributes that match exactly or all of these attributes to match"
  ([^org.keycloak.admin.client.Keycloak keycloak-client realm-name user-attribute]
   (let [users (search-user keycloak-client realm-name user-attribute)]
     (if (and users (> (clojure.core/count users) 0))
       (let [^org.keycloak.representations.idm.UserRepresentation user (exact-match users user-attribute)]
         (if user
           (.getId user)
           (do (info "user with attribute"user-attribute"not found in realm"realm-name) nil)))
       (do (info "user with attribute"user-attribute"not found in realm"realm-name) nil))))
  ([^org.keycloak.admin.client.Keycloak keycloak-client realm-name username first-name last-name email]
   (let [users (search-user keycloak-client realm-name username first-name last-name email)]
     (if (and users (> (clojure.core/count users) 0))
       (let [^org.keycloak.representations.idm.UserRepresentation user (exact-match users username first-name last-name email)]
         (if user
           (.getId user)
           (do (info "user with attributes username:"username ",first-name" first-name ",last-name:" last-name ",email:" email "not found in realm"realm-name) nil)))
       (do (info "user with attributes username:"username ",first-name" first-name ",last-name:" last-name ",email:" email "not found in realm"realm-name) nil)))))

(defn delete-user!
  "delete user with any attribute"
  [^org.keycloak.admin.client.Keycloak keycloak-client realm-name user-attribute]
  (info "Delete user with attribute"user-attribute"from realm"realm-name)
  (let [user-id (user-id keycloak-client realm-name user-attribute)]
    (if user-id
      (-> keycloak-client (.realm realm-name) (.users) (.delete user-id))
      (info "user"user-attribute"not found in realm"realm-name))
    user-id))

(defn username-or-email-exists? [^org.keycloak.admin.client.Keycloak keycloak-client realm-name ^org.keycloak.representations.idm.UserRepresentation user]
  (let [username-exists? (not (nil? (user-id keycloak-client realm-name (.getUsername user))))
        email-exists?    (not (nil? (user-id keycloak-client realm-name (.getEmail user))))]
    (or username-exists? email-exists?)))

(defn get-user-resource
  [^org.keycloak.admin.client.Keycloak keycloak-client realm-name username]
  (let [user-id     (user-id keycloak-client realm-name username)
        ^org.keycloak.admin.client.resource.UserResource user-resource (when user-id (-> keycloak-client (.realm realm-name) (.users) (.get user-id)))]
    (if (and user-id user-resource)
      {:user-id       user-id
       :user-resource user-resource}
      (throw (ex-info (format "User %s in realm %s not found! (user-id %s)" username realm-name user-id) {:username username :realm-name realm-name :user-id user-id})))))

(defn get-user-realm-roles [^org.keycloak.admin.client.Keycloak keycloak-client realm-name username]
  (map bean/RoleRepresentation->map (-> (get-user-resource keycloak-client realm-name username)
                                        :user-resource
                                        (.roles)
                                        (.realmLevel)
                                        (.listAll))))

(defn- get-realm-roles-representations ^RoleRepresentation [^org.keycloak.admin.client.Keycloak keycloak-client realm-name roles]
  (doall (map (fn [role]
                (try
                  (-> keycloak-client
                      (.realm realm-name)
                      (.roles)
                      (.get role)
                      (.toRepresentation))
                  (catch javax.ws.rs.NotFoundException nfe
                    (warn "Realm role" role "not found in realm" realm-name)))) (map name roles))))

(def memoized-get-realm-roles-representations (memoize get-realm-roles-representations))

(defn add-realm-roles!
  [^org.keycloak.admin.client.Keycloak keycloak-client realm-name username roles]
  (when roles
    (let [{:keys [user-resource]}      (get-user-resource keycloak-client realm-name username)
          roles-representations-to-add (memoized-get-realm-roles-representations keycloak-client realm-name roles)]
      (println "user and roles" username user-resource roles-representations-to-add)
      (when user-resource
        (-> ^org.keycloak.admin.client.resource.UserResource user-resource
            (.roles)
            (.realmLevel)
            (.add (java.util.ArrayList. ^java.util.Collection (vec (filter some? roles-representations-to-add))))))
      roles)))

(defn remove-realm-roles!
  [^org.keycloak.admin.client.Keycloak keycloak-client realm-name username roles]
  (when roles
    (let [{:keys [user-resource]} (get-user-resource keycloak-client realm-name username)
          roles-representations   (memoized-get-realm-roles-representations keycloak-client realm-name roles)]
      (-> ^org.keycloak.admin.client.resource.UserResource user-resource
          (.roles)
          (.realmLevel)
          (.remove (java.util.ArrayList. ^java.util.Collection (vec (filter some? roles-representations)))))
      roles)))

(defn set-realm-roles!
  [^org.keycloak.admin.client.Keycloak keycloak-client realm-name username roles]
  (when roles
    (let [{:keys [user-resource]} (get-user-resource keycloak-client realm-name username)
          roles-representations   (get-realm-roles-representations keycloak-client realm-name roles)
          role-scope-resource     (-> ^org.keycloak.admin.client.resource.UserResource user-resource (.roles) (.realmLevel))]
      (.remove role-scope-resource (.listEffective role-scope-resource))
      (.add role-scope-resource (java.util.ArrayList. ^java.util.Collection (vec (filter some? roles-representations))))
      roles)))

(defn get-client
  ^org.keycloak.representations.idm.ClientRepresentation
  [^org.keycloak.admin.client.Keycloak keycloak-client realm-name client-id]
  (-> keycloak-client (.realm realm-name) (.clients) (.findByClientId client-id) first))

(defn add-client-roles!
  [^org.keycloak.admin.client.Keycloak keycloak-client realm-name username client-roles]
  (let [{:keys [user-resource]} (get-user-resource keycloak-client realm-name username)]
    (when (not-empty client-roles) (info (format "Add client roles %s to user %s" (pr-str client-roles ) username)))
    (doseq [[client-id roles] client-roles]
      (let [client (get-client keycloak-client realm-name client-id)
            roles-representations (doall (map (fn [role]
                                                (try
                                                  (-> keycloak-client
                                                      (.realm realm-name)
                                                      (.clients)
                                                      (.get (.getId client))
                                                      (.roles)
                                                      (.get role)
                                                      (.toRepresentation))
                                                  (catch javax.ws.rs.NotFoundException nfe
                                                    (log/error "Client role" role "not found in realm" realm-name"for client" client-id)))) (map name roles)))]
        (-> ^org.keycloak.admin.client.resource.UserResource user-resource
            (.roles)
            (.clientLevel (.getId client))
            (.add (java.util.ArrayList. ^java.util.Collection (vec (filter some? roles-representations)))))))))



(defn- check-user-properly-created [^org.keycloak.admin.client.Keycloak keycloak-client realm-name username email]
  (let [user-searched (search-user keycloak-client realm-name username)]
    (when (or (nil? user-searched) (empty? user-searched))
      (throw (ex-info (str "Can't find user " username
                           " due to creation failure. Check the uniqueness and"
                           " validity of the username and email in Keycloak (email:"email")")
                      {:username username :email email})))))

(defn get-user
  ^org.keycloak.representations.idm.UserRepresentation [^org.keycloak.admin.client.Keycloak keycloak-client realm-name user-id]
  (info "get user [id=" user-id "] in realm " realm-name)
  (-> keycloak-client (.realm realm-name) (.users) (.get user-id) (.toRepresentation)))

(defn find-users
  [^org.keycloak.admin.client.Keycloak keycloak-client realm-name s]
  (info "find user by username, email etc." s)
  (-> keycloak-client (.realm realm-name) (.users) (.search s (int 0) (int 1000))))

(defn get-user-by-username
  ^org.keycloak.representations.idm.UserRepresentation [^org.keycloak.admin.client.Keycloak keycloak-client realm-name username]
  (first (find-users keycloak-client realm-name username)))

(defn delete-and-create-user!
  ([^org.keycloak.admin.client.Keycloak keycloak-client realm-name person]
   (delete-and-create-user! keycloak-client realm-name person nil nil))
  ([^org.keycloak.admin.client.Keycloak keycloak-client realm-name {:keys [username first-name last-name email password]
                                :as person} realm-roles client-roles]
   (info "create user" username "in realm" realm-name"with realm roles"realm-roles"client roles"client-roles". If user already exists, delete it and re-create it.")
   (let [username-exists? (not (nil? (user-id keycloak-client realm-name username)))
         email-exists?    (not (nil? (user-id keycloak-client realm-name email)))
         _                (if username-exists?
                            (do (delete-user! keycloak-client realm-name username) (Thread/sleep 100)))
         _                (if email-exists?
                            (do (delete-user! keycloak-client realm-name email) (Thread/sleep 100)))
         response (-> keycloak-client (.realm realm-name) (.users) (.create (user-for-creation person)))
         user-id  (extract-id response)]
     (when response (.close response))
     (check-user-properly-created keycloak-client realm-name username email)
     (when realm-roles  (add-realm-roles! keycloak-client realm-name username realm-roles))
     (when client-roles (add-client-roles! keycloak-client realm-name username client-roles))
     (if user-id
       (get-user keycloak-client realm-name user-id)
       (get-user-by-username keycloak-client realm-name username)))))

(defn update-user! [^org.keycloak.admin.client.Keycloak keycloak-client realm-name user-id {:keys [username first-name last-name email password] :as person}]
  (-> keycloak-client (.realm realm-name) (.users) (.get user-id) (.update (user-for-update person)))
  (get-user keycloak-client realm-name user-id))

(defn enable-user! [^org.keycloak.admin.client.Keycloak keycloak-client realm-name username]
  (.update (:user-resource (get-user-resource keycloak-client realm-name username)) (user-for-enablement true))
  (get-user-by-username keycloak-client realm-name username))

(defn disable-user! [^org.keycloak.admin.client.Keycloak keycloak-client realm-name username]
  (.update (:user-resource (get-user-resource keycloak-client realm-name username)) (user-for-enablement false))
  (get-user-by-username keycloak-client realm-name username))

(defn create-user!
  ^org.keycloak.representations.idm.UserRepresentation [^org.keycloak.admin.client.Keycloak keycloak-client realm-name {:keys [username first-name last-name email password is-manager group in-subgroups] :as person}]
  (info "create user" username "in realm" realm-name)
  (let [resp    (-> keycloak-client (.realm realm-name) (.users) (.create (user-for-creation person)))
        user-id (extract-id resp)]
    (when resp (.close resp))
    (info "user with username " username "created in realm" realm-name " with id" user-id)
    (if user-id
      (get-user keycloak-client realm-name user-id)
      (get-user-by-username keycloak-client realm-name username))))

(defn username-exists? [^org.keycloak.admin.client.Keycloak keycloak-client realm-name username]
  (not (nil? (user-id keycloak-client realm-name username))))

(defn create-or-update-user!
  ^org.keycloak.representations.idm.UserRepresentation [^org.keycloak.admin.client.Keycloak keycloak-client realm-name {:keys [username first-name last-name email password] :as person} realm-roles client-roles]
  (let [_                (info "Create or update user" username "in realm" realm-name "with realm roles" realm-roles "client roles"client-roles)
        username-exists? (not (nil? (user-id keycloak-client realm-name username)))
        email-exists?    (not (nil? (user-id keycloak-client realm-name email)))
        user-id          (user-id keycloak-client realm-name username)]
    (if username-exists? (info (str "User already exists with username: " username)))
    (if email-exists? (info (str "User already exists with email: " email)))
    (when (and email-exists? (not username-exists?))
      (warn (str "Email " email " already exists in realm " realm-name))
                                        ;(throw (ex-info (str "Email " email " already exists in realm " realm-name) {:realm-name realm-name :email email})
      )
    (try
      (let [user (if (and username-exists? user-id)
                   (do (update-user! keycloak-client realm-name user-id person))
                   (do (create-user! keycloak-client realm-name person)))]
        (check-user-properly-created keycloak-client realm-name username email)
        (set-realm-roles! keycloak-client realm-name username realm-roles)
        (add-client-roles! keycloak-client realm-name username client-roles)
        user)
      (catch javax.ws.rs.ClientErrorException cee
        (warn "Exception while creating or updating " person (.getMessage cee))))))

(defn count [^org.keycloak.admin.client.Keycloak keycloak-client realm-name]
  (-> keycloak-client (.realm realm-name) (.users) (.count )))

(defn get-users ^java.util.List
  ([^org.keycloak.admin.client.Keycloak keycloak-client realm-name]
   (get-users keycloak-client realm-name (Integer/valueOf 0) (count keycloak-client realm-name)))
  ([^org.keycloak.admin.client.Keycloak keycloak-client realm-name first result]
   (-> keycloak-client (.realm realm-name) (.users) (.list (Integer/valueOf first)  (Integer/valueOf result)))))

(defn get-users-beans [^org.keycloak.admin.client.Keycloak keycloak-client realm-name]
  (map bean/ClientRepresentation->map (get-users keycloak-client realm-name)))

(defn get-users-with-realm-role
  "return a list of users as UserRepresentation that have the `role-name` as role mapping"
  ^java.util.List [^org.keycloak.admin.client.Keycloak keycloak-client realm-name role-name]
  (-> keycloak-client (.realm realm-name) (.roles) (.get role-name) (.getRoleUserMembers (Integer. 0) (Integer/MAX_VALUE))))

(defn get-users-aggregated-by-realm-roles [^org.keycloak.admin.client.Keycloak keycloak-client realm-name roles]
  (into {} (map (fn [role]
                 [role (get-users-with-realm-role keycloak-client realm-name role)]) roles)))

(defn get-client-resource
  "Return a [org.keycloak.admin.client.resource.ClientResource](https://www.keycloak.org/docs-api/11.0/javadocs/org/keycloak/admin/client/resource/ClientResource.html)
  given a `keycloak-client`, `realm-name` and `id`. Be careful the id is the UUID attributed by Keycloak during the creation of the client and not the `clientId` given by the user"
  ^ClientResource [^Keycloak keycloak-client realm-name client-id]
  (-> keycloak-client (.realm realm-name) (.clients) (.get client-id)))

(defn get-users-with-client-role
  "return a list of users as UserRepresentation that have the `role-name` as role mapping"
  ^java.util.List [^org.keycloak.admin.client.Keycloak keycloak-client realm-name client-id role-name]
  (-> (get-client-resource keycloak-client realm-name client-id) (.roles) (.get role-name) (.getRoleUserMembers)))

(defn get-users-aggregated-by-client-roles [^org.keycloak.admin.client.Keycloak keycloak-client realm-name client-id roles]
  (into {} (map (fn [role]
                 [role (get-users-with-client-role keycloak-client realm-name client-id role)]) roles)))

(defn logout-user!
  [^org.keycloak.admin.client.Keycloak keycloak-client realm-name user-id]
  (-> keycloak-client
      (.realm realm-name)
      (.users)
      (.get user-id)
      (.logout)))

(defn user-representation->person [user-rep]
  (bean/UserRepresentation->map user-rep))

(defn rand-str [len]
  (apply str (take len (repeatedly #(char (+ (rand 26) 65))))))

(defn generate-username
  ([] (generate-username "fake-user-"))
  ([prefix]
   (str prefix (rand-int 1000))))

(defn generate-user
  ([] (generate-user (generate-username)))
  ([username]
   (merge (talltale/person)
          {:username username
           :email (talltale/fixed-email username)
           :attributes {"testattr1" [(rand-str 16)]}
           :password "password"})))

(defn generate-user-representation
  ([] (generate-user-representation (generate-username)))
  ([username]
   (user-for-update (generate-user username))))

;(-> admin-client (.realm "electre-localhost") (.users) (.search "" 0 Integer/MAX_VALUE))
