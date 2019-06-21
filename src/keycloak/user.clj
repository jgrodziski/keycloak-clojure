(ns keycloak.user
  (:require [clojure.tools.logging :as log :refer [info warn]]
            [clojure.java.data :refer [from-java]]
            [clojure.java.io :as io]
            [cheshire.core :as json :refer [encode]])
  (:import [org.keycloak.representations.idm CredentialRepresentation UserRepresentation RoleRepresentation]))

(defn user-for-update [{:keys [username first-name last-name email] :as person} roles]
  (doto (UserRepresentation.)
    (.setUsername username)
    (.setFirstName first-name)
    (.setLastName last-name)
    (.setEmail email)
    (.setEnabled true)
    ;;setRealmRoles has a bug with the admin REST API and doesn't work
    ))

(defn user-for-creation
  ([{:keys [username first-name last-name email password] :as person}]
   (doto (user-for-update person nil)
     (.setCredentials [(doto (CredentialRepresentation.)
                         (.setType CredentialRepresentation/PASSWORD)
                         (.setValue password))])))
  ([{:keys [username first-name last-name email password] :as person} required-actions]
   (doto (user-for-update person nil)
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

(defn add-roles!
  [keycloak-client realm-name username roles]
  (let [user-searched (search-user keycloak-client realm-name username)
        user-id (-> user-searched first (.getId))
        user-resource (-> keycloak-client (.realm realm-name) (.users) (.get user-id))
        roles-representations (doall (map (fn [role]
                                            (-> keycloak-client
                                                (.realm realm-name)
                                                (.roles)
                                                (.get role)
                                                (.toRepresentation))) (map name roles)))]
    (-> user-resource
        (.roles)
        (.realmLevel)
        (.add (java.util.ArrayList. (vec roles-representations))))))

(defn- check-user-properly-created [keycloak-client realm-name username email]
  (let [user-searched (search-user keycloak-client realm-name username)]
    (when (or (nil? user-searched) (empty? user-searched))
      (throw (ex-info (str "Can't find user " username
                           " due to creation failure. Check the uniqueness and"
                           " validity of the username and email in Keycloak (email:"email")")
                      {:username username :email email})))))

(defn delete-and-create-user!
  ([keycloak-client realm-name person]
   (delete-and-create-user! keycloak-client realm-name person nil))
  ([keycloak-client realm-name {:keys [username first-name last-name email password is-manager]
                                :as person} roles]
   (info "create user" username "in realm" realm-name"with roles"roles". If user already exists, delete it and re-create it.")
   (let [username-exists? (not (nil? (user-id keycloak-client realm-name username)))
         email-exists? (not (nil? (user-id keycloak-client realm-name email)))
         _ (if username-exists? (do (delete-user! keycloak-client realm-name username) (Thread/sleep 250)))
         _ (if email-exists? (do (delete-user! keycloak-client realm-name email) (Thread/sleep 250)))
         response (-> keycloak-client (.realm realm-name) (.users) (.create (user-for-creation person)))
         _ (check-user-properly-created keycloak-client realm-name username email)]
     (when roles (add-roles! keycloak-client realm-name username roles))
     person)))

(defn create-or-update-user!
  [keycloak-client realm-name {:keys [username first-name last-name email password is-manager] :as person} roles]
  (let [_ (info "Create or update user" username "in realm" realm-name "with roles" (roles person))
        username-exists? (not (nil? (user-id keycloak-client realm-name username)))
        email-exists? (not (nil? (user-id keycloak-client realm-name email)))
        user-id (user-id keycloak-client realm-name username first-name last-name email)]
    (if username-exists? (info (str "User already exists with username: " username)))
    (if email-exists? (info (str "User already exists with email: " email)))
    (when (and email-exists? (not username-exists?))
      (warn (str "Email " email " already exists in realm " realm-name))
                                        ;(throw (ex-info (str "Email " email " already exists in realm " realm-name) {:realm-name realm-name :email email})
      )
    (try
      (if (and username-exists? user-id)
        (-> keycloak-client (.realm realm-name) (.users) (.get user-id) (.update (user-for-update person roles)))
        (-> keycloak-client (.realm realm-name) (.users) (.create (user-for-creation person))))
      (catch javax.ws.rs.ClientErrorException cee
        (warn "Exception while creating or updating " person (.getMessage cee))))
    (check-user-properly-created keycloak-client realm-name username email)
    (add-roles! keycloak-client realm-name username roles)))

(defn get-users
  ([keycloak-client realm-name]
   (-> keycloak-client (.realm realm-name) (.users) (.list))))
