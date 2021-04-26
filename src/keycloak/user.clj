(ns keycloak.user
  (:require [clojure.tools.logging :as log :refer [info warn]]
            [clojure.string :as string :refer [last-index-of]]
            [clojure.java.data :refer [from-java]]
            [clojure.java.io :as io]
            [cheshire.core :as json :refer [encode]]
            [keycloak.utils :as utils :refer [hint-typed-doto set-attributes]])
  (:import [org.keycloak.representations.idm CredentialRepresentation UserRepresentation RoleRepresentation]
           [javax.ws.rs.core Response]))

(set! *warn-on-reflection* true)

(defn extract-id [^Response resp]
  (when resp
    (when-let [loc (.getLocation resp)]
      (subs (str loc) (+ (last-index-of (str loc) "/") 1)))))

(defn user-for-update
  ^org.keycloak.representations.idm.UserRepresentation
  [{:keys [username first-name last-name email enabled attributes password] :or {enabled true} :as person}]
  (set-attributes ^org.keycloak.representations.idm.UserRepresentation
                  (hint-typed-doto "org.keycloak.representations.idm.UserRepresentation" (UserRepresentation.)
                    (.setUsername username)
                    (.setFirstName first-name)
                    (.setLastName last-name)
                    (.setEmail email)
                    (.setCredentials [(hint-typed-doto "org.keycloak.representations.idm.CredentialRepresentation" (CredentialRepresentation.)
                                                       (.setType CredentialRepresentation/PASSWORD)
                                                       (.setValue password))])
                    (.setEnabled enabled)
                    ;;setRealmRoles has a bug with the admin REST API and doesn't work
                    ) attributes))

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

(defn user-id
  ([^org.keycloak.admin.client.Keycloak keycloak-client realm-name user-attribute]
   (let [search-result (search-user keycloak-client realm-name user-attribute)]
     (if (and search-result (> (count search-result) 0))
       (let [^org.keycloak.representations.idm.UserRepresentation user (first search-result)]
         (.getId user))
       (do (info "user with attribute"user-attribute"not found in realm"realm-name) nil))))
  ([^org.keycloak.admin.client.Keycloak keycloak-client realm-name username first-name last-name email]
   (let [search-result (search-user keycloak-client realm-name username first-name last-name email)]
     (if (and search-result (> (count search-result) 0))
       (let [^org.keycloak.representations.idm.UserRepresentation user (first search-result)]
         (.getId user))
       (do (info "user with attributes username:"username ",first-name" first-name ",last-name:" last-name ",email:" email "not found in realm"realm-name) nil)))))

(defn delete-user!
  "delete user with any attribute"
  [^org.keycloak.admin.client.Keycloak keycloak-client realm-name user-attribute]
  (info "Delete user with attribute"user-attribute"from realm"realm-name)
  (let [user-id (user-id keycloak-client realm-name user-attribute)]
    (if user-id
      (-> keycloak-client (.realm realm-name) (.users) (.delete user-id))
      (info "user"user-attribute"not found in realm"realm-name))))

(defn username-or-email-exists? [^org.keycloak.admin.client.Keycloak keycloak-client realm-name ^org.keycloak.representations.idm.UserRepresentation user]
  (let [username-exists? (not (nil? (user-id keycloak-client realm-name (.getUsername user))))
        email-exists? (not (nil? (user-id keycloak-client realm-name (.getEmail user))))]
    (or username-exists? email-exists?)))

(defn- get-user-resource
  [^org.keycloak.admin.client.Keycloak keycloak-client realm-name username]
  (let [user-searched (search-user keycloak-client realm-name username)
        ^org.keycloak.representations.idm.UserRepresentation user (-> user-searched first)
        user-id     (.getId user) 
        ^org.keycloak.admin.client.resource.UserResource user-resource (-> keycloak-client (.realm realm-name) (.users) (.get user-id))]
    {:user-searched user-searched
     :user-id       user-id
     :user-resource user-resource}))

(defn- get-realm-roles-representations
  ^org.keycloak.representations.idm.RoleRepresentation
  [^org.keycloak.admin.client.Keycloak keycloak-client realm-name roles]
  (doall (map (fn [role]
                (try
                  (-> keycloak-client
                      (.realm realm-name)
                      (.roles)
                      (.get role)
                      (.toRepresentation))
                  (catch javax.ws.rs.NotFoundException nfe
                    (warn "Realm role" role "not found in realm" realm-name)))) (map name roles))))

(defn add-realm-roles!
  [^org.keycloak.admin.client.Keycloak keycloak-client realm-name username roles]
  (when roles
    (let [{:keys [user-searched user-id user-resource]} (get-user-resource keycloak-client realm-name username)
          roles-representations                         (get-realm-roles-representations keycloak-client realm-name roles)]
      (-> ^org.keycloak.admin.client.resource.UserResource user-resource
          (.roles)
          (.realmLevel)
          (.add (java.util.ArrayList. ^java.util.Collection (vec (filter some? roles-representations))))))))


(defn remove-realm-roles!
  [^org.keycloak.admin.client.Keycloak keycloak-client realm-name username roles]
  (when roles
    (let [{:keys [user-searched user-id user-resource]} (get-user-resource keycloak-client realm-name username)
          roles-representations                         (get-realm-roles-representations keycloak-client realm-name roles)]
      (-> ^org.keycloak.admin.client.resource.UserResource user-resource
          (.roles)
          (.realmLevel)
          (.remove (java.util.ArrayList. ^java.util.Collection (vec (filter some? roles-representations))))))))

(defn set-realm-roles!
  [^org.keycloak.admin.client.Keycloak keycloak-client realm-name username roles]
  (when roles
    (let [{:keys [user-searched user-id user-resource]} (get-user-resource keycloak-client realm-name username)
          roles-representations                         (get-realm-roles-representations keycloak-client realm-name roles)
          role-scope-resource                           (-> ^org.keycloak.admin.client.resource.UserResource user-resource (.roles) (.realmLevel))]
      (.remove role-scope-resource (.listEffective role-scope-resource))
      (.add role-scope-resource (java.util.ArrayList. ^java.util.Collection (vec (filter some? roles-representations)))))))

(defn get-client
  ^org.keycloak.representations.idm.ClientRepresentation
  [^org.keycloak.admin.client.Keycloak keycloak-client realm-name client-id]
  (-> keycloak-client (.realm realm-name) (.clients) (.findByClientId client-id) first))

(defn add-client-roles!
  [^org.keycloak.admin.client.Keycloak keycloak-client realm-name username client-roles]
  (let [{:keys [user-searched user-id user-resource]} (get-user-resource keycloak-client realm-name username)]
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
         email-exists? (not (nil? (user-id keycloak-client realm-name email)))
         _ (if username-exists? (do (delete-user! keycloak-client realm-name username) (Thread/sleep 100)))
         _ (if email-exists? (do (delete-user! keycloak-client realm-name email) (Thread/sleep 100)))
         response (-> keycloak-client (.realm realm-name) (.users) (.create (user-for-creation person)))
         user-id (extract-id response)
         _ (check-user-properly-created keycloak-client realm-name username email)]
     (when realm-roles (add-realm-roles! keycloak-client realm-name username realm-roles))
     (when client-roles (add-client-roles! keycloak-client realm-name username client-roles))
     (if user-id
       (get-user keycloak-client realm-name user-id)
       (get-user-by-username keycloak-client realm-name username)))))


(defn update-user! [^org.keycloak.admin.client.Keycloak keycloak-client realm-name user-id {:keys [username first-name last-name email password] :as person}]
  (-> keycloak-client (.realm realm-name) (.users) (.get user-id) (.update (user-for-update person)))
  (get-user keycloak-client realm-name user-id))

(defn create-user!
  ^org.keycloak.representations.idm.UserRepresentation [^org.keycloak.admin.client.Keycloak keycloak-client realm-name {:keys [username first-name last-name email password is-manager] :as person}]
  (info "create user" username "in realm" realm-name)
  (let [resp (-> keycloak-client (.realm realm-name) (.users) (.create (user-for-creation person)))
        user-id (extract-id resp)]
    (.close resp)
    (info "user with username " username "created in realm" realm-name " with id" user-id)
    (if user-id
      (get-user keycloak-client realm-name user-id)
      (get-user-by-username keycloak-client realm-name username))))

(defn create-or-update-user!
  ^org.keycloak.representations.idm.UserRepresentation [^org.keycloak.admin.client.Keycloak keycloak-client realm-name {:keys [username first-name last-name email password] :as person} realm-roles client-roles]
  (let [_                (info "Create or update user" username "in realm" realm-name "with realm roles" realm-roles "client roles"client-roles)
        username-exists? (not (nil? (user-id keycloak-client realm-name username)))
        email-exists?    (not (nil? (user-id keycloak-client realm-name email)))
        user-id          (user-id keycloak-client realm-name username first-name last-name email)]
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
        (add-realm-roles! keycloak-client realm-name username realm-roles)
        (add-client-roles! keycloak-client realm-name username client-roles)
        user)
      (catch javax.ws.rs.ClientErrorException cee
        (warn "Exception while creating or updating " person (.getMessage cee))))))

(defn get-users
  ([^org.keycloak.admin.client.Keycloak keycloak-client realm-name]
   (-> keycloak-client (.realm realm-name) (.users) (.list))))

(defn logout-user!
  [^org.keycloak.admin.client.Keycloak keycloak-client realm-name user-id]
  (-> keycloak-client
      (.realm realm-name)
      (.users)
      (.get user-id)
      (.logout)))
