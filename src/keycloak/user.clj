(ns keycloak.user
  (:require [clojure.tools.logging :as log :refer [info warn]]
            [clojure.string :as string :refer [last-index-of]]
            [clojure.java.data :refer [from-java]]
            [clojure.java.io :as io]
            [cheshire.core :as json :refer [encode]])
  (:import [org.keycloak.representations.idm CredentialRepresentation UserRepresentation RoleRepresentation]
           [javax.ws.rs.core Response]))

(defn extract-id [^Response resp]
  (when resp
    (when-let [loc (.getLocation resp)]
      (subs (str loc) (+ (last-index-of (str loc) "/") 1)))))

(defn set-attributes [representation attributes]
  (if (and attributes (not-empty (filter some? attributes)))
    (doto representation
      (.setAttributes (java.util.HashMap. attributes)))
    representation))

(defn user-for-update [{:keys [username first-name last-name email enabled attributes] :as person}]
  (set-attributes (doto (UserRepresentation.)
                    (.setUsername username)
                    (.setFirstName first-name)
                    (.setLastName last-name)
                    (.setEmail email)
                    (.setEnabled enabled)
                    ;;setRealmRoles has a bug with the admin REST API and doesn't work
                    ) attributes))

(defn user-for-creation
  ([{:keys [username first-name last-name email password attributes] :as person}]
   (when (empty? password) (throw (ex-info "user MUST have a password otherwise the login will throw a NPE" {:person person})))
   (doto (user-for-update person)
     (.setCredentials [(doto (CredentialRepresentation.)
                         (.setType CredentialRepresentation/PASSWORD)
                         (.setValue password))])))
  ([{:keys [username first-name last-name email password attributes] :as person} required-actions]
   (doto (user-for-update person)
     (.setRequiredActions (java.util.ArrayList. required-actions))
                                        ;(.setRequiredActions (java.util.ArrayList. ["UPDATE_PASSWORD"]))
     (.setCredentials [(doto (CredentialRepresentation.)
                         (.setType CredentialRepresentation/PASSWORD)
                         (.setValue password))]))))

(defn search-user
  ([keycloak-client realm-name user-attribute]
   (try
     (-> keycloak-client (.realm realm-name) (.users) (.search user-attribute (int 0) (int 10)))
     (catch javax.ws.rs.NotFoundException nfe nil)))
  ([keycloak-client realm-name username first-name last-name email]
   (try
     (-> keycloak-client (.realm realm-name) (.users) (.search username first-name last-name email (int 0) (int 10)))
     (catch javax.ws.rs.NotFoundException nfe nil))))

(defn user-id
  ([keycloak-client realm-name user-attribute]
   (let [search-result (search-user keycloak-client realm-name user-attribute)]
     (if (and search-result (> (count search-result) 0))
       (.getId (first search-result))
       (do (info "user with attribute"user-attribute"not found in realm"realm-name) nil))))
  ([keycloak-client realm-name username first-name last-name email]
   (let [search-result (search-user keycloak-client realm-name username first-name last-name email)]
     (if (and search-result (> (count search-result) 0))
       (.getId (first search-result))
       (do (info "user with attributes username:"username ",first-name" first-name ",last-name:" last-name ",email:" email "not found in realm"realm-name) nil)))))

(defn delete-user!
  "delete user with any attribute"
  [keycloak-client realm-name user-attribute]
  (info "Delete user with attribute"user-attribute"from realm"realm-name)
  (let [user-id (user-id keycloak-client realm-name user-attribute)]
    (if user-id
      (-> keycloak-client (.realm realm-name) (.users) (.delete user-id))
      (info "user"user-attribute"not found in realm"realm-name))))

(defn username-or-email-exists? [keycloak-client realm-name user]
  (let [username-exists? (not (nil? (user-id keycloak-client realm-name (.getUsername user))))
        email-exists? (not (nil? (user-id keycloak-client realm-name (.getEmail user))))]
    (or username-exists? email-exists?)))

(defn add-realm-roles!
  [keycloak-client realm-name username roles]
  (when roles
    (let [user-searched (search-user keycloak-client realm-name username)
          user-id (-> user-searched first (.getId))
          user-resource (-> keycloak-client (.realm realm-name) (.users) (.get user-id))
          roles-representations (doall (map (fn [role]
                                              (try
                                                (-> keycloak-client
                                                    (.realm realm-name)
                                                    (.roles)
                                                    (.get role)
                                                    (.toRepresentation))
                                                (catch javax.ws.rs.NotFoundException nfe
                                                  (warn "Realm role" role "not found in realm" realm-name)))) (map name roles)))]
      (-> user-resource
          (.roles)
          (.realmLevel)
          (.add (java.util.ArrayList. (vec (filter some? roles-representations))))))))

(defn remove-realm-roles!
  [keycloak-client realm-name username roles]
  (when roles
    (let [user-searched (search-user keycloak-client realm-name username)
          user-id (-> user-searched first (.getId))
          user-resource (-> keycloak-client (.realm realm-name) (.users) (.get user-id))
          roles-representations (doall (map (fn [role]
                                              (try
                                                (-> keycloak-client
                                                    (.realm realm-name)
                                                    (.roles)
                                                    (.get role)
                                                    (.toRepresentation))
                                                (catch javax.ws.rs.NotFoundException nfe
                                                  (warn "Realm role" role "not found in realm" realm-name)))) (map name roles)))]
      (-> user-resource
          (.roles)
          (.realmLevel)
          (.remove (java.util.ArrayList. (vec (filter some? roles-representations))))))))

(defn set-realm-roles!
  [keycloak-client realm-name username roles]
  (when roles
    (let [user-searched (search-user keycloak-client realm-name username)
          user-id (-> user-searched first (.getId))
          user-resource (-> keycloak-client (.realm realm-name) (.users) (.get user-id))
          roles-representations (doall (map (fn [role]
                                              (try
                                                (-> keycloak-client
                                                    (.realm realm-name)
                                                    (.roles)
                                                    (.get role)
                                                    (.toRepresentation))
                                                (catch javax.ws.rs.NotFoundException nfe
                                                  (warn "Realm role" role "not found in realm" realm-name)))) (map name roles)))
          role-scope-resource (-> user-resource (.roles) (.realmLevel))]
      (.remove role-scope-resource (.listEffective role-scope-resource))
      (.add role-scope-resource (java.util.ArrayList. (vec (filter some? roles-representations)))))))

(defn get-client
  [keycloak-client realm-name client-id]
  (-> keycloak-client (.realm realm-name) (.clients) (.findByClientId client-id) first))

(defn add-client-roles!
  [keycloak-client realm-name username client-roles]
  (let [user-searched (search-user keycloak-client realm-name username)
        user-id (-> user-searched first (.getId))
        user-resource (-> keycloak-client (.realm realm-name) (.users) (.get user-id))]
    (when (not-empty client-roles) (println (format "Add client roles %s to user %s" (pr-str client-roles ) username)))
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
                                                    (println "Client role" role "not found in realm" realm-name"for client" client-id)))) (map name roles)))]
        (-> user-resource
            (.roles)
            (.clientLevel (.getId client))
            (.add (java.util.ArrayList. (vec (filter some? roles-representations)))))))))

(defn- check-user-properly-created [keycloak-client realm-name username email]
  (let [user-searched (search-user keycloak-client realm-name username)]
    (when (or (nil? user-searched) (empty? user-searched))
      (throw (ex-info (str "Can't find user " username
                           " due to creation failure. Check the uniqueness and"
                           " validity of the username and email in Keycloak (email:"email")")
                      {:username username :email email})))))

(defn get-user
  [keycloak-client realm-name user-id]
  (info "get user [id=" user-id "] in realm " realm-name)
  (-> keycloak-client (.realm realm-name) (.users) (.get user-id) (.toRepresentation)))

(defn find-users
  [keycloak-client realm-name s]
  (info "find user by username, email etc." s)
  (-> keycloak-client (.realm realm-name) (.users) (.search s (int 0) (int 1000))))

(defn get-user-by-username
  [keycloak-client realm-name username]
  (first (find-users keycloak-client realm-name username)))

(defn delete-and-create-user!
  ([keycloak-client realm-name person]
   (delete-and-create-user! keycloak-client realm-name person nil))
  ([keycloak-client realm-name {:keys [username first-name last-name email password is-manager]
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


(defn update-user! [keycloak-client realm-name user-id {:keys [username first-name last-name email password is-manager] :as person}]
  (-> keycloak-client (.realm realm-name) (.users) (.get user-id) (.update (user-for-update person)))
  (get-user keycloak-client realm-name user-id))

(defn create-user!
  [keycloak-client realm-name {:keys [username first-name last-name email password is-manager] :as person}]
  (info "create user" username "in realm" realm-name)
  (let [resp (-> keycloak-client (.realm realm-name) (.users) (.create (user-for-creation person)))
        user-id (extract-id resp)]
    (.close resp)
    (info "user with username " username "created in realm" realm-name " with id" user-id)
    (if user-id
      (get-user keycloak-client realm-name user-id)
      (get-user-by-username keycloak-client realm-name username))))

(defn create-or-update-user!
  [keycloak-client realm-name {:keys [username first-name last-name email password is-manager] :as person} realm-roles client-roles]
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
                   (update-user! keycloak-client realm-name user-id person)
                   (create-user! keycloak-client realm-name person))]
        (check-user-properly-created keycloak-client realm-name username email)
        (add-realm-roles! keycloak-client realm-name username realm-roles)
        (add-client-roles! keycloak-client realm-name username client-roles)
        user)
      (catch javax.ws.rs.ClientErrorException cee
        (warn "Exception while creating or updating " person (.getMessage cee))))))

(defn get-users
  ([keycloak-client realm-name]
   (-> keycloak-client (.realm realm-name) (.users) (.list))))

(defn logout-user!
  [keycloak-client realm-name user-id]
  (-> keycloak-client
      (.realm realm-name)
      (.users)
      (.get user-id)
      (.logout)))
